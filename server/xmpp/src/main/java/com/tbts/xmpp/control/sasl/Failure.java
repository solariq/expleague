package com.tbts.xmpp.control.sasl;

import com.tbts.xmpp.Item;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 16:08
 */
@XmlRootElement
public class Failure extends Item {
  @XmlElement
  protected String aborted;
  @XmlElement(name = "account-disabled")
  protected String accountDisabled;
  @XmlElement(name = "credentials-expired")
  protected String credentialsExpired;
  @XmlElement(name = "encryption-required")
  protected String encryptionRequired;
  @XmlElement(name = "incorrect-encoding")
  protected String incorrectEncoding;
  @XmlElement(name = "invalid-authzid")
  protected String invalidAuthzid;
  @XmlElement(name = "invalid-mechanism")
  protected String invalidMechanism;
  @XmlElement(name = "malformed-request")
  protected String malformedRequest;
  @XmlElement(name = "mechanism-too-weak")
  protected String mechanismTooWeak;
  @XmlElement(name = "not-authorized")
  protected String notAuthorized;
  @XmlElement(name = "temporary-auth-failure")
  protected String temporaryAuthFailure;
  @XmlElement(name = "transition-needed")
  protected String transitionNeeded;

  @XmlElement
  protected String text;

  public Failure() {}

  public Failure(Type type, String text) {
    switch (type) {
      case ABORTED:
        aborted = "";
        break;
      case ACCOUNT_DISABLED:
        accountDisabled = "";
        break;
      case CREDENTIALS_EXPIRED:
        credentialsExpired = "";
        break;
      case ENCRYPTION_REQUIRED:
        encryptionRequired = "";
        break;
      case INCORRECT_ENCODING:
        incorrectEncoding = "";
        break;
      case INVALID_AUTHZID:
        invalidAuthzid = "";
        break;
      case INVALID_MECHANISM:
        invalidMechanism = "";
        break;
      case MALFORMED_REQUEST:
        malformedRequest = "";
        break;
      case MECHANISM_TOO_WEAK:
        mechanismTooWeak = "";
        break;
      case NOT_AUTHORIZED:
        notAuthorized = "";
        break;
      case TEMPORARY_AUTH_FAILURE:
        temporaryAuthFailure = "";
        break;
      case TRANSITION_NEEDED:
        transitionNeeded = "";
        break;
    }
    this.text = text;
  }


  public enum Type {
    ACCOUNT_DISABLED,
    CREDENTIALS_EXPIRED,
    ENCRYPTION_REQUIRED,
    INCORRECT_ENCODING,
    INVALID_AUTHZID,
    INVALID_MECHANISM,
    MALFORMED_REQUEST,
    MECHANISM_TOO_WEAK,
    NOT_AUTHORIZED,
    TEMPORARY_AUTH_FAILURE,
    ABORTED, TRANSITION_NEEDED
  }
}
