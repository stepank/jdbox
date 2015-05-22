package jdbox.utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;

/**
 * https://gist.github.com/fappel/8bcb2aea4b39ff9cfb6e
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({METHOD, ANNOTATION_TYPE})
public @interface Repeat {
    int value() default 1;
}
