package com.tbts.util.xml;

import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.spbsu.commons.func.Factory;

/**
 * User: solar
 * Date: 06.12.15
 * Time: 17:11
 */
public interface AsyncXMLBuilder<T> extends Factory<T> {
  boolean accept(int token, AsyncXMLStreamReader reader);
}
