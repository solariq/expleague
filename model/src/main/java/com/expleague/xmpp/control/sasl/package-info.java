/**
 * User: solar
 * Date: 11.12.15
 * Time: 16:12
 */
@XmlSchema(
    namespace = "urn:ietf:params:xml:ns:xmpp-sasl",
    elementFormDefault = XmlNsForm.UNQUALIFIED,
    xmlns = {@XmlNs(prefix = "", namespaceURI = "urn:ietf:params:xml:ns:xmpp-sasl")}
)
package com.expleague.xmpp.control.sasl;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;