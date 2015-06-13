package act.util;

import act.asm.AnnotationVisitor;
import act.asm.Opcodes;
import act.asm.Type;
import org.osgl._;
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.S;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

public class GenericAnnoInfo {

    public static class EnumInfo {
        private Type type;
        private String value;

        EnumInfo(String desc, String value) {
            this.type = Type.getType(desc);
            this.value = value;
        }

        public Type type() {
            return type;
        }

        public String value() {
            return value;
        }
    }

    private Type type;
    private Map<String, Object> attributes = C.newMap();
    private Map<String, List<Object>> listAttributes = C.newMap();

    public GenericAnnoInfo(Type type) {
        E.NPE(type);
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public Class annotationClass(ClassLoader classLoader) {
        return _.classForName(type.getClassName(), classLoader);
    }

    public Map<String, Object> attributes() {
        return C.map(attributes);
    }

    public Map<String, List<Object>> listAttributes() {
        return C.map(listAttributes);
    }

    public GenericAnnoInfo addAnnotation(String name, Type type) {
        GenericAnnoInfo anno = new GenericAnnoInfo(type);
        attributes.put(name, anno);
        return anno;
    }

    public void putAttribute(String name, Object val) {
        attributes.put(name, val);
    }

    public void putListAttribute(String name, Object val) {
        List<Object> vals = listAttributes.get(name);
        if (null == vals) {
            vals = C.newList(val);
            listAttributes.put(name, vals);
        } else {
            vals.add(val);
        }
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public List<Object> getListAttributes(String name) {
        return listAttributes.get(name);
    }

    @Override
    public int hashCode() {
        return _.hc(type, attributes, listAttributes);
    }

    @Override
    public String toString() {
        C.List<String> keys = C.newList(attributes.keySet()).append(listAttributes.keySet()).sort();
        StringBuilder sb = S.builder().append("@").append(type.getClassName()).append("(");
        for (String key: keys) {
            Object v = attributes.get(key);
            if (null == v) {
                v = listAttributes.get(v);
            }
            sb.append(key).append("=").append(v).append(", ");
        }
        if (!keys.isEmpty()) {
            sb.delete(sb.length() - 2, sb.length());
        }
        sb.append(")");
        return sb.toString();
    }

    public <T extends Annotation> T toAnnotation() {
        return AnnotationInvocationHandler.proxy(this);
    }

    public static class Visitor extends AnnotationVisitor implements Opcodes {
        private GenericAnnoInfo anno;

        public Visitor(AnnotationVisitor av, GenericAnnoInfo anno) {
            super(ASM5, av);
            E.NPE(anno);
            this.anno = anno;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            AnnotationVisitor av = super.visitAnnotation(name, desc);
            GenericAnnoInfo annoAnno = anno.addAnnotation(name, Type.getType(desc));
            return av;
        }

        @Override
        public void visit(String name, Object value) {
            anno.putAttribute(name, value);
            super.visit(name, value);
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
            anno.putAttribute(name, new EnumInfo(desc, value));
            super.visitEnum(name, desc, value);
        }

        @Override
        public AnnotationVisitor visitArray(final String name) {
            AnnotationVisitor av = super.visitArray(name);
            return new AnnotationVisitor(ASM5, av) {
                @Override
                public void visitEnum(String ignore, String desc, String value) {
                    anno.putListAttribute(name, new EnumInfo(desc, value));
                }

                @Override
                public void visit(String ignore, Object value) {
                    anno.putListAttribute(name, value);
                }
            };
        }
    }

    public static class AnnotationInvocationHandler<T extends Annotation> implements Annotation, InvocationHandler, Serializable {
        private static final long serialVersionUID = 8157022630814320170L;
        private final GenericAnnoInfo annoInfo;
        private final Class<T> annotationType;
        private final int hashCode;

        AnnotationInvocationHandler(GenericAnnoInfo annoInfo, ClassLoader classLoader) {
            this.annotationType = _.classForName(annoInfo.type().getClassName(), classLoader);
            this.annoInfo = annoInfo;
            this.hashCode = annoInfo.hashCode();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String key = method.getName();
            Object retVal = annoInfo.getAttribute(key);
            if (null != retVal) return retVal;
            retVal = toArray(annoInfo.getListAttributes(key));
            if (null != retVal) return retVal;
            return method.invoke(this, args);
        }

        private <T> Object toArray(List<Object> list) {
            Class<T> c = (Class<T>)list.get(0).getClass();
            int size = list.size();
            if (c == String.class) {
                return list.toArray(new String[size]);
            } else if (c == Class.class) {
                return list.toArray(new Class[size]);
            } else if (c == Boolean.class) {
                return _.asPrimitive(list.toArray(new Boolean[size]));
            } else if (c == Byte.class) {
                return _.asPrimitive(list.toArray(new Byte[size]));
            } else if (c == Short.class) {
                return _.asPrimitive(list.toArray(new Short[size]));
            } else if (c == Character.class) {
                return _.asPrimitive(list.toArray(new Character[size]));
            } else if (c == Integer.class) {
                return _.asPrimitive(list.toArray(new Integer[size]));
            } else if (c == Float.class) {
                return _.asPrimitive(list.toArray(new Float[size]));
            } else if (c == Long.class) {
                return _.asPrimitive(list.toArray(new Long[size]));
            } else if (c == Double.class) {
                return _.asPrimitive(list.toArray(new Double[size]));
            } else {
                return list.toArray((T[])Array.newInstance(c, size));
            }
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return annotationType;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!annotationType.isInstance(obj)) {
                return false;
            }

            Annotation other = annotationType.cast(obj);

            //compare annotation member values
            for (Map.Entry<String, Object> member : annoInfo.attributes.entrySet()) {
                Object value = member.getValue();
                Object otherValue = getAnnotationMemberValue(other, member.getKey());
                if (!_.eq2(value, otherValue)) {
                    return false;
                }
            }
            for (Map.Entry<String, List<Object>> member : annoInfo.listAttributes.entrySet()) {
                Object value = toArray(member.getValue());
                Object otherValue = getAnnotationMemberValue(other, member.getKey());
                if (!_.eq2(value, otherValue)) {
                    return false;
                }
            }

            return true;
        }


        /**
         * Calculates the hash code of this annotation proxy as described in
         * {@link Annotation#hashCode()}.
         *
         * @return The hash code of this proxy.
         * @see Annotation#hashCode()
         */
        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return annoInfo.toString();
        }


        private Object getAnnotationMemberValue(Annotation annotation, String name) {
            try {
                Method method = annotation.getClass().getMethod(name, new Class<?>[]{});
                return method.invoke(annotation);
            } catch (Exception e) {
                throw E.unexpected(e);
            }
        }

        public static <T extends Annotation> T proxy(GenericAnnoInfo info) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            return proxy(info, cl);
        }

        public static <T extends Annotation> T proxy(GenericAnnoInfo info, ClassLoader cl) {
            AnnotationInvocationHandler handler = new AnnotationInvocationHandler(info, cl);
            return (T) Proxy.newProxyInstance(cl, new Class[]{handler.annotationType()}, handler);
        }

    }
}