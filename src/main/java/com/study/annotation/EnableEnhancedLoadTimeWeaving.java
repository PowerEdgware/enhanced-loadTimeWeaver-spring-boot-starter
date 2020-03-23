package com.study.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.EnableLoadTimeWeaving.AspectJWeaving;

//用于增强 Aspect weaver的注解

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableEnhancedLoadTimeWeaving {

	/**
	 * Whether AspectJ weaving should be enabled.
	 */
	AspectJWeaving aspectjWeaving() default AspectJWeaving.AUTODETECT;


}
