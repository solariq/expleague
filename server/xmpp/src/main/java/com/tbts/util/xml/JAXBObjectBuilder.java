package com.tbts.util.xml;

import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.spbsu.commons.func.Action;
import com.spbsu.commons.system.RuntimeUtils;
import com.spbsu.commons.util.Holder;
import com.spbsu.commons.util.MultiMap;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collection;

/**
 * User: solar
 * Date: 06.12.15
 * Time: 17:11
 */
public class JAXBObjectBuilder<T> implements AsyncXMLBuilder<T> {
  private final String name;
  private final String ns;
  private final Class<T> targetClass;
  private final MultiMap<Field, AsyncXMLBuilder<?>> fieldFactories = new MultiMap<>();
  private Action<String> valueAction;

  public JAXBObjectBuilder(String name, String ns, Class<T> targetClass) {
    this.name = name;
    this.ns = ns;
    this.targetClass = targetClass;
    RuntimeUtils.processSupers(targetClass, aClass -> {
      try {
        for (final Field field : aClass.getDeclaredFields()) {
          for (final Annotation annotation : field.getDeclaredAnnotations()) {
            final Package aPackage = annotation.annotationType().getPackage();
            if (aPackage == null || !"javax.xml.bind.annotation".equals(aPackage.getName()))
              continue;
            processAnnotation(field, annotation, fieldFactories);
          }
        }
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
      return false;
    });
  }

  private void processAnnotation(Field associated, Annotation annotation, MultiMap<Field, AsyncXMLBuilder<?>> factories) throws NoSuchFieldException {
    associated.setAccessible(true);
    if (annotation instanceof XmlElements) {
      for (final XmlElement element : ((XmlElements) annotation).value()) {
        processAnnotation(associated, element, factories);
      }
    }
    else if (annotation instanceof XmlElement) {
      final XmlElement element = (XmlElement) annotation;
      //noinspection unchecked
      factories.put(associated, new JAXBObjectBuilder<>(element.name(), element.namespace(), element.type()));
    }
    else if (annotation instanceof XmlAttribute) {
      final XmlAttribute attribute = (XmlAttribute) annotation;
      final String name = "##default".equals(attribute.name()) ? associated.getName() : attribute.name();
      final String ns = "##default".equals(attribute.namespace()) ? null : attribute.namespace();
      final AsyncXMLBuilder builder;
      if (associated.getType().isPrimitive() || String.class.equals(associated.getType())) {
        builder = new AsyncXMLBuilder() {
          private final Holder<Object> primitiveHolder = new Holder<>();
          @Override
          public Object create() {
            return primitiveHolder.getValue();
          }
          @Override
          public boolean accept(int token, AsyncXMLStreamReader reader) {
            if (token != XMLEvent.ATTRIBUTE)
              return false;
            try {
              for (int i = 0; i < reader.getAttributeCount(); i++) {
                if (reader.getAttributeLocalName(i).equals(name) && (ns == null || ns.equals(reader.getAttributeNamespace(i)))) {
                  final Class<?> associatedType = associated.getType();
                  if (String.class.equals(associatedType)) {
                    primitiveHolder.setValue(reader.getAttributeValue(i));
                  } else if (int.class.equals(associatedType)) {
                    primitiveHolder.setValue(reader.getAttributeAsInt(i));
                  } else if (long.class.equals(associatedType)) {
                    primitiveHolder.setValue(reader.getAttributeAsLong(i));
                  } else if (boolean.class.equals(associatedType)) {
                    primitiveHolder.setValue(reader.getAttributeAsBoolean(i));
                  } else {
                    throw new UnsupportedOperationException();
                  }
                  return true;
                }
              }
              return false;
            }
            catch (XMLStreamException e) {
              throw new RuntimeException(e);
            }
          }
        };
      }
      else { // assuming value is enough
        //noinspection unchecked
        builder = new JAXBObjectFromAttrBuilder(associated, name, ns);
      }
      factories.put(associated, builder);
    }
    else if (annotation instanceof XmlValue) {
      final Class<?> associatedType = associated.getType();
      if (String.class.equals(associatedType)) {
        valueAction = text -> {
          try {
            associated.set(instance, text);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        };
      }
      else if (associatedType.isPrimitive()) {
        throw new UnsupportedOperationException();
      }
      else {
        //noinspection unchecked
        final JAXBObjectBuilder builder = new JAXBObjectBuilder(null, null, associated.getType());
        valueAction = text -> {
          try {
            builder.start();
            associated.set(instance, builder.finish(text));
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        };
      }
    }
  }

  private T instance;
  protected StringBuilder valueHarvester = new StringBuilder();
  private int deep;
  @Override
  public boolean accept(int token, AsyncXMLStreamReader reader) {
    if (instance != null) {
      for (final Field field : fieldFactories.keySet()) {
        for (final AsyncXMLBuilder<?> builder : fieldFactories.get(field)) {
          try {
            if (builder.accept(token, reader)) {
              final Object value = field.get(instance);
              final Object result = builder.create();
              if (value != null) {
                if (value instanceof Collection) {
                  //noinspection unchecked
                  ((Collection) value).add(result);
                }
                else
                  throw new IllegalArgumentException("Collision! Field [" + field.getName() + "@" + field.getDeclaringClass().getName()
                                                         + "] is already set to: [" + value + "], but found in stream again with value: [" + result
                                                         + "] while being not a collection.");
              }
              else field.set(instance, result);
            }
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        }
      }
      deep += (token == XMLEvent.START_ELEMENT ? 1 : 0);
      deep -= (token == XMLEvent.END_ELEMENT ? 1 : 0);
      return deep == 0;
    }
    else if (token == XMLEvent.CHARACTERS) {
      valueHarvester.append(reader.getTextCharacters());
    }
    else if (token == XMLEvent.START_ELEMENT) {
        final QName qName = reader.getName();
        if (name.equals(qName.getLocalPart()) && (ns == null || ns.equals(qName.getNamespaceURI())))
          start();
      }
    return false;
  }

  public boolean acceptFlat(int token, AsyncXMLStreamReader reader, Action todo) {
    if (instance != null) {
      for (final Field field : fieldFactories.keySet()) {
        //noinspection unchecked
        fieldFactories.get(field).stream().filter(builder -> builder.accept(token, reader)).forEach(builder -> todo.invoke(builder.create()));
      }
      deep += (token == XMLEvent.START_ELEMENT ? 1 : 0);
      deep -= (token == XMLEvent.END_ELEMENT ? 1 : 0);
      return deep == 0;
    }
    else if (token == XMLEvent.START_ELEMENT) {
        final QName qName = reader.getName();
        if (name.equals(qName.getLocalPart()) && (ns == null || ns.equals(qName.getNamespaceURI()))) {
          start();
        }
      }
    return false;
  }

  void start() {
    try {
      instance = targetClass.newInstance();
      valueHarvester.delete(0, valueHarvester.length());
      deep = 0;
    } catch (InstantiationException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  T finish(String sequence) {
    if (valueAction != null)
      valueAction.invoke(sequence);
    final T result = this.instance;
    checkInstance(result);
    this.instance = null;
    return result;
  }

  @Override
  public T create() {
    if (deep != 0)
      throw new IllegalStateException();
    return finish(valueHarvester.toString());
  }

  @SuppressWarnings("UnusedParameters")
  private void checkInstance(T instance) {
  }

  private static class JAXBObjectFromAttrBuilder<T> extends JAXBObjectBuilder<T> {
    private final String name;
    private final String ns;

    public JAXBObjectFromAttrBuilder(Field associated, String name, String ns) {
      //noinspection unchecked
      super(null, null, (Class<T>)associated.getType());
      this.name = name;
      this.ns = ns;
    }

    @Override
    public boolean accept(int token, AsyncXMLStreamReader reader) {
      for (int i = 0; i < reader.getAttributeCount(); i++) {
        if (name.equals(reader.getAttributeLocalName(i)) && (ns == null || ns.equals(reader.getAttributeNamespace(i)))) {
          start();
          this.valueHarvester.append(reader.getAttributeValue(i));
          return true;
        }
      }
      return false;
    }
  }
}
