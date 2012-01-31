package com.trendmicro.tme.grapheditor;

import java.util.Map;

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.core.spi.component.ioc.IoCComponentProvider;
import com.sun.jersey.core.spi.component.ioc.IoCComponentProviderFactory;
import com.sun.jersey.core.spi.component.ioc.IoCFullyManagedComponentProvider;

public class IocProviderFactory implements IoCComponentProviderFactory {
    private Map<Class<?>, Object> classMap;
    
    public IocProviderFactory(Map<Class<?>, Object> classMap) {
        this.classMap = classMap;
    }
    
    @Override
    public IoCComponentProvider getComponentProvider(Class<?> c) {
        final Object o = classMap.get(c);
        if(o == null) {
            return null;
        }
        return new IoCFullyManagedComponentProvider() {
            @Override
            public Object getInstance() {
                return o;
            }
            
            @Override
            public ComponentScope getScope() {
                return ComponentScope.Singleton;
            }
        };
    }
    
    @Override
    public IoCComponentProvider getComponentProvider(ComponentContext cc, Class<?> c) {
        return getComponentProvider(c);
    }
}
