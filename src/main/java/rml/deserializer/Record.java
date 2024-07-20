package rml.deserializer;

import mods.rml.api.announces.EarlyClass;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/20 19:00
 *
 * Act as record in higher java, used for auto construction.
 * If labeled in a CONSTRUCTOR, you should fill parameters if existed.
 * If labeled in a TYPE, it just a node, nothing need to do specially.
 *
 * Note: You could only label one constructor for each record class.
 **/
@EarlyClass
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value={CONSTRUCTOR, TYPE})
public @interface Record {
    /**
     * @return the parameters list
     */
    Parameter[] parameters() default {};

    @EarlyClass
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(value={PARAMETER})
    @interface Parameter{
        /**
         * @return the real type, could be primitive
         */
        Class<?> type();

        /**
         * @return the name of this parameter
         */
        String name();
    }
}
