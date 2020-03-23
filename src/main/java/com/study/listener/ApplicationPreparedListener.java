package com.study.listener;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.EnableLoadTimeWeaving.AspectJWeaving;
import org.springframework.context.weaving.AspectJWeavingEnabler;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import com.study.annotation.EnableEnhancedLoadTimeWeaving;
/*
 * Activates a Spring {@link LoadTimeWeaver} for this application context, available as
 * a bean with the name "loadTimeWeaver", similar to the {@code <context:load-time-weaver>}
 * element in Spring XML.
 * 
 * SpringApplication Listener
 */
public class ApplicationPreparedListener implements ApplicationListener<ApplicationPreparedEvent> {

	public static final String ASPECTJ_WEAVING_ENABLER_BEAN_NAME = "org.springframework.context.config.internalAspectJWeavingEnabler";

	static final String ASPECTJ_WEAVING_ENABLER_CLASS_NAME = "org.springframework.context.weaving.AspectJWeavingEnabler";

	static final String DEFAULT_LOAD_TIME_WEAVER_CLASS_NAME = "org.springframework.context.weaving.DefaultContextLoadTimeWeaver";

	static Logger log = LoggerFactory.getLogger(ApplicationPreparedListener.class);

	@Override
	public void onApplicationEvent(ApplicationPreparedEvent event) {
		ConfigurableApplicationContext context = event.getApplicationContext();
		SpringApplication application = event.getSpringApplication();

		Set<Class<?>> clazzSet = transform(application.getAllSources(), context.getClassLoader());

		AnnotationAttributes annotationAttributes = findEnhancedLoadTimeWeaverAnno(clazzSet);
		if (annotationAttributes != null) {
			// 注册load-time-weaver BeanDefinition
			registerLoadTimeWeaverBeanDefinetion(context, application);

			context.addBeanFactoryPostProcessor(new LoadTimeWeavingRegistryPostProcessor(annotationAttributes));
		}
	}

	private void registerLoadTimeWeaverBeanDefinetion(ConfigurableApplicationContext context,
			SpringApplication application) {
		// 注册load-time weaver
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition();
		builder.getRawBeanDefinition().setBeanClassName(DEFAULT_LOAD_TIME_WEAVER_CLASS_NAME);
		builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		builder.getRawBeanDefinition().setSource(application);

		BeanDefinitionHolder holder = new BeanDefinitionHolder(builder.getBeanDefinition(),
				ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME);

		BeanDefinitionRegistry registry = (BeanDefinitionRegistry) context.getBeanFactory();
		BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);

	}

	private Set<Class<?>> transform(Set<Object> sources, ClassLoader classLoader) {
		return sources.stream().filter(x -> {
			return x instanceof String || x instanceof Class;
		}).map(x -> {
			if (x instanceof String) {
				try {
					return ClassUtils.forName(x.toString(), classLoader);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
			}
			return (Class<?>) x;
		}).collect(Collectors.toSet());
	}

	private AnnotationAttributes findEnhancedLoadTimeWeaverAnno(Set<Class<?>> weaverAnnotationClazzSet) {
		ArrayList<AnnotationAttributes> filteredList = weaverAnnotationClazzSet.stream().map(clazz -> {
			StandardAnnotationMetadata annotationMetadata = new StandardAnnotationMetadata(clazz);
			AnnotationAttributes annotationAttributes = AnnotationAttributes.fromMap(
					annotationMetadata.getAnnotationAttributes(EnableEnhancedLoadTimeWeaving.class.getName(), false));
			if (annotationAttributes != null) {
				log.debug("Found @EnableEnhancedLoadTimeWeaving on class '"+clazz+"'");
				return annotationAttributes;
			}
			return null;
		}).filter(x -> x != null).collect(Collectors.toCollection(ArrayList::new));
		if (filteredList.size() > 1) {
			throw new RuntimeException("More than one configuration class annotate with @EnableEnhancedLoadTimeWeaving"
					+ " ' " + weaverAnnotationClazzSet + " '");
		}

		return filteredList.isEmpty() ? null : filteredList.get(0);
	}

	/*
	 * {@link ConfigurationClassPostProcessor} Spring Configuration class Processor
	 * @author caicai
	 *
	 * @see LoadTimeWeaver
	 * @see DefaultContextLoadTimeWeaver
	 * @see org.aspectj.weaver.loadtime.ClassPreProcessorAgentAdapter
	 */
	private static class LoadTimeWeavingRegistryPostProcessor
			implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

		static Logger log = LoggerFactory.getLogger(LoadTimeWeavingRegistryPostProcessor.class);

		@Nullable
		private AnnotationAttributes enableLTW;

		public LoadTimeWeavingRegistryPostProcessor(AnnotationAttributes annotationAttributes) {
			this.enableLTW = annotationAttributes;
		}

		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
			if (enableLTW == null) {
				log.debug("No class is annotated with @EnableEnhancedLoadTimeWeaving");
				return;
			}
			if (registry instanceof DefaultListableBeanFactory) {
				DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) registry;
				boolean switchOn = isWeaverEnabled(beanFactory.getBeanClassLoader());

				if (beanFactory.containsBeanDefinition(ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME)) {
					if (switchOn) {
						// 先于业务类被加载时，添加load-time-weaver，实现在业务bean加载时做字节码增强
						LoadTimeWeaver loadTimeWeaver = (LoadTimeWeaver) beanFactory
								.getBean(ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME);
						AspectJWeavingEnabler.enableAspectJWeaving(loadTimeWeaver, beanFactory.getBeanClassLoader());
					}
				}
			}
		}

		// 和优先级高于 ConfigurationClassPostProcessor#postProcessBeanDefinitionRegistry 的加载
		@Override
		public int getOrder() {
			return Ordered.HIGHEST_PRECEDENCE;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

		}

		private boolean isWeaverEnabled(ClassLoader beanClassLoader) {
			boolean switchOn = false;
			AspectJWeaving aspectJWeaving = this.enableLTW.getEnum("aspectjWeaving");
			switch (aspectJWeaving) {
			case DISABLED:
				// AJ weaving is disabled -> do nothing
				break;
			case AUTODETECT:
				if (beanClassLoader.getResource(AspectJWeavingEnabler.ASPECTJ_AOP_XML_RESOURCE) == null) {
					// No aop.xml present on the classpath -> treat as 'disabled'
					break;
				}
				// aop.xml is present on the classpath -> enable
				switchOn = true;
				break;
			case ENABLED:
				switchOn = true;
				break;
			}
			return switchOn;
		}

	}

}
