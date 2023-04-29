# enhanced-loadTimeWeaver-spring-boot-starter

目的
解决springboot autoconfiguration下 @EnableLoadTimeWeaving导致Aspectj静态代理失效的bug。

通过自定义增强的EnableEnhancedLoadTimeWeaving注解，利用spring EventListener机制
实现在springboot刷新上下文之前注入ClassFileTransformer，避免类加载完毕 后@Aspect静态织入失效的问题。

使用方式：

1，引入该jar

<dependency>
			<groupId>com.study.boot</groupId>
			<artifactId>enhanced-loadTimeWeaver-spring-boot-starter</artifactId>
			<optional>1.0.1</optional>
		</dependency>
2.在标注@SpringBootApplication类上标注注解：EnableEnhancedLoadTimeWeaving

例子：

@SpringBootApplication
//@EnableLoadTimeWeaving(aspectjWeaving = AspectJWeaving.ENABLED)// 此方式有bug，aspect无法织入目标对象
//@ImportResource(locations = "classpath:aop-aspectj-via-loadtimeweaver.xml")  这种方式也不行

//TODO 自定义实现
@EnableEnhancedLoadTimeWeaving(aspectjWeaving = AspectJWeaving.DISABLED)
public class LoadTimeWeaverApp extends SpringBootServletInitializer /* implements LoadTimeWeavingConfigurer*/ {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(LoadTimeWeaverApp.class);
	}
	
	public static void main(String[] args) {
		//invoke StartApp
		SpringApplication.run(LoadTimeWeaverApp.class, args);
		
	}
}
3.打包项目，部署到Servlet容器，比如tomcat

或者使用内嵌的tomcat独立运行，此方式必须在启动函数添加：-javaagent参数 。javaAgent例子
