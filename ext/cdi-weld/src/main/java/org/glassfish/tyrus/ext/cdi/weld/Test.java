package org.glassfish.tyrus.ext.cdi.weld;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Inject;
import javax.inject.Scope;

import org.jboss.weld.environment.se.Weld;

@SuppressWarnings("PackageAccessibility")
public class Test {

    @Inject
    private Instance<A> a;

    public void run() {

        System.out.println("#1 " + CDI.current());
        System.out.println("#1 " + a);

        try {
            System.out.println("#1a1 " + a.get());
        } catch (Exception e) {
            e.printStackTrace();
        }

        MyScopeContext.setActive(true);
        System.out.println("#1a2 " + a.get());
        System.out.println("#1a3 " + a.get());

        MyScopeContext.setActive(false);
        System.out.println("#1a4 " + a.get());

        System.out.println("#1 " + CDI.current());

    }


    public static void main(String[] args) {
        final Weld weld = new Weld();

        weld.initialize().instance().select(Test.class).get().run();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                weld.shutdown();
            }
        });
    }

    @MyScope
    public static class A {

    }

    @Scope
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    public static @interface MyScope {

    }

    public static class MyScopeExtension implements Extension, Serializable {
        public void addScope(@Observes final BeforeBeanDiscovery event) {
            event.addScope(MyScope.class, true, false);
        }

        public void registerContext(@Observes final AfterBeanDiscovery event) {
            event.addContext(new MyScopeContext());
        }
    }

    public static class MyScopeContext implements Context, Serializable {

        private Logger log = Logger.getLogger(getClass().getSimpleName());

        private CustomScopeContextHolder customScopeContextHolder;
        private static volatile boolean active = false;

        public MyScopeContext() {
            log.info("Init");
            this.customScopeContextHolder = CustomScopeContextHolder.getInstance();
        }

        @Override
        public <T> T get(final Contextual<T> contextual) {
            Bean bean = (Bean) contextual;
            if (customScopeContextHolder.getBeans().containsKey(bean.getBeanClass())) {
                return (T) customScopeContextHolder.getBean(bean.getBeanClass()).instance;
            } else {
                return null;
            }
        }

        @Override
        public <T> T get(final Contextual<T> contextual, final CreationalContext<T> creationalContext) {
            Bean bean = (Bean) contextual;
            if (customScopeContextHolder.getBeans().containsKey(bean.getBeanClass())) {
                return (T) customScopeContextHolder.getBean(bean.getBeanClass()).instance;
            } else {
                T t = (T) bean.create(creationalContext);
                CustomScopeInstance customInstance = new CustomScopeInstance();
                customInstance.bean = bean;
                customInstance.ctx = creationalContext;
                customInstance.instance = t;
                customScopeContextHolder.putBean(customInstance);
                return t;
            }
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return MyScope.class;
        }

        public boolean isActive() {
            return active;
        }

        public static void setActive(boolean active) {
            MyScopeContext.active = active;
        }

        //        public void destroy(@Observes KillEvent killEvent) {
//            if (customScopeContextHolder.getBeans().containsKey(killEvent.getBeanType())) {
//                customScopeContextHolder.destroyBean(customScopeContextHolder.getBean(killEvent.getBeanType()));
//            }
//        }
    }

    public static class CustomScopeContextHolder implements Serializable {

        private static CustomScopeContextHolder INSTANCE;

        //we will have only one instance of a type so the key is a class
        private Map<Class, CustomScopeInstance> beans;

        private CustomScopeContextHolder() {
            beans = Collections.synchronizedMap(new HashMap<Class, CustomScopeInstance>());
        }

        public synchronized static CustomScopeContextHolder getInstance() {
            if (INSTANCE == null) {
                INSTANCE = new CustomScopeContextHolder();
            }
            return INSTANCE;
        }

        public Map<Class, CustomScopeInstance> getBeans() {
            return beans;
        }

        public CustomScopeInstance getBean(Class type) {
            System.out.println("### getBean " + type);
            return getBeans().get(type);
        }

        public void putBean(CustomScopeInstance customInstance) {
            System.out.println("### putBean " + customInstance);
            getBeans().put(customInstance.bean.getBeanClass(), customInstance);
        }

        void destroyBean(CustomScopeInstance customScopeInstance) {
            getBeans().remove(customScopeInstance.bean.getBeanClass());
            customScopeInstance.bean.destroy(customScopeInstance.instance, customScopeInstance.ctx);
        }
    }

    /**
     * wrap necessary properties so we can destroy the bean later:
     *
     * @see CustomScopeContextHolder#destroyBean(org.glassfish.tyrus.ext.cdi.weld.Test.CustomScopeInstance)
     */
    public static class CustomScopeInstance<T> {

        Bean<T> bean;
        CreationalContext<T> ctx;
        T instance;
    }
}