package com.expleague.util.stream;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * User: Artem
 * Date: 23.06.2017
 */
@Retention(value = RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface RequiresClose {
}
