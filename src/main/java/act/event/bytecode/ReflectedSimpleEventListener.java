package act.event.bytecode;

import act.Act;
import act.app.App;
import act.event.SimpleEventListener;
import act.inject.DependencyInjector;
import act.inject.param.ParamValueLoaderService;
import org.osgl.$;
import org.osgl.inject.BeanSpec;
import org.osgl.util.C;
import org.osgl.util.E;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

class ReflectedSimpleEventListener implements SimpleEventListener {

    private transient volatile Object host;

    private final String className;
    private final String methodName;
    private C.List<Class> paramTypes;
    private C.List<Class> providedParamTypes;
    private final Method method;
    private final int providedParamSize;
    private final boolean isStatic;

    ReflectedSimpleEventListener(String className, String methodName, List<BeanSpec> paramTypes, boolean isStatic) {
        this.className = $.notNull(className);
        this.methodName = $.notNull(methodName);
        this.isStatic = isStatic;
        this.paramTypes = C.newList();
        this.providedParamTypes = C.newList();
        DependencyInjector injector = Act.injector();
        boolean cutOff = false;
        if (null == paramTypes) {
            paramTypes = C.list();
        }
        for (int i = paramTypes.size() - 1; i >= 0; --i) {
            BeanSpec spec = paramTypes.get(i);
            if (ParamValueLoaderService.provided(spec, injector)) {
                E.unexpectedIf(cutOff, "provided(injected) argument must be put at the end of passed in argument list");
                providedParamTypes.add(spec.rawType());
            } else {
                cutOff = true;
                this.paramTypes.add(spec.rawType());
            }
        }
        this.paramTypes = this.paramTypes.reverse();
        this.providedParamSize = this.providedParamTypes.size();
        if (providedParamSize > 0) {
            this.providedParamTypes = this.providedParamTypes.reverse();
        }
        Class[] argList = new Class[paramTypes.size()];
        for (int i = 0; i < argList.length; ++i) {
            argList[i] = paramTypes.get(i).rawType();
        }
        method = $.getMethod($.classForName(className, Act.app().classLoader()), methodName, argList);
    }

    @Override
    public void invoke(Object... args) {
        try {
            int paramNo = paramTypes.size();
            int argsNo = args.length;
            Object[] realArgs = args;
            if (paramNo != argsNo || providedParamSize > 0) {
                realArgs = new Object[paramNo + providedParamSize];
                System.arraycopy(args, 0, realArgs, 0, Math.min(paramNo, argsNo));
                App app = Act.app();
                for (int i = 0; i < providedParamSize; ++i) {
                    realArgs[i + paramNo] = app.getInstance(providedParamTypes.get(i));
                }
            }
            method.invoke(host(), realArgs);
        } catch (IllegalAccessException e) {
            throw E.unexpected(e);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            throw E.unexpected(t, "Error executing event listener method %s.%s", className, methodName);
        }
    }

    private Object host() {
        if (isStatic) {
            return null;
        } else {
            if (null == host) {
                synchronized (this) {
                    if (null == host) {
                        App app = App.instance();
                        host = app.getInstance($.classForName(className, app.classLoader()));
                    }
                }
            }
            return host;
        }
    }

}
