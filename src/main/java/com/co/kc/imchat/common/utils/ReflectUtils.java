package com.co.kc.imchat.common.utils;

import com.co.kc.imchat.common.exception.ReflectException;
import com.co.kc.imchat.common.function.SFunction;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author kc
 */
public class ReflectUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectUtils.class);

    private static final Pattern GETTER_PATTERN = Pattern.compile("^(get|is|has)(.+)$");
    private static final String GETTER_METHOD_PREFIX = "get";
    private static final String SETTER_METHOD_PREFIX = "set";

    private ReflectUtils() {
    }

    public static String getterMethodName(String fieldName) {
        return GETTER_METHOD_PREFIX + capitalize(fieldName);
    }

    public static String setterMethodName(String fieldName) {
        return SETTER_METHOD_PREFIX + capitalize(fieldName);
    }

    public static Method getterMethod(Class<?> clazz, String fieldName) {
        try {
            return clazz.getMethod(getterMethodName(fieldName));
        } catch (NoSuchMethodException e) {
            throw new ReflectException("Fail to get 'getterMethod' Method", e);
        }
    }

    public static Method setterMethod(Class<?> clazz, String fieldName, Class<?> parameterTypes) throws NoSuchMethodException {
        try {
            return clazz.getMethod(setterMethodName(fieldName), parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new ReflectException("Fail to get 'setterMethod' Method", e);
        }
    }

    /**
     * 从方法引用中提取属性名（支持 getter 方法）
     */
    public static <T, R> String getPropertyName(SFunction<T, R> methodRef) {
        try {
            // 获取方法引用的序列化形式
            Method writeReplace = methodRef.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda serializedLambda = (SerializedLambda) writeReplace.invoke(methodRef);
            // 获取方法名
            String methodName = serializedLambda.getImplMethodName();
            // 处理 getter 方法
            Matcher matcher = GETTER_PATTERN.matcher(methodName);
            if (!matcher.matches()) {
                // 直接返回方法名（非标准 getter）
                return methodName;
            }
            String propertyPart = matcher.group(2);
            return decapitalize(propertyPart);
        } catch (Exception e) {
            throw new ReflectException("Fail to extract property name from method reference", e);
        }
    }

    public static String generateBeanName(Class<?> clazz) {
        String className = clazz.getSimpleName();
        return capitalize(className);
    }

    public static Object invokeGetterMethod(Object o, String fieldName) {
        try {
            Method getMethod = getterMethod(o.getClass(), fieldName);
            getMethod.setAccessible(true);
            return getMethod.invoke(o);
        } catch (Exception e) {
            throw new ReflectException("Fail to invoke 'invokeGetMethod' Method", e);
        }
    }

    public static void invokeSetterMethod(Object o, String fieldName, Object args) {
        try {
            Method setMethod = setterMethod(o.getClass(), fieldName, args.getClass());
            setMethod.setAccessible(true);
            setMethod.invoke(o, args);
        } catch (Exception e) {
            throw new ReflectException("Fail to invoke 'invokeSetMethod' Method", e);
        }
    }

    public static void invokeSetterMethod(Object o, String fieldName, Class<?> parameterTypes, Object args) {
        try {
            Method setMethod = setterMethod(o.getClass(), fieldName, parameterTypes);
            setMethod.invoke(o, args);
        } catch (Exception e) {
            throw new ReflectException("Fail to invoke 'invokeSetMethod' Method", e);
        }
    }

    public static <T> T replaceNewValue(Object oldObj, Object updateObj, Class<T> clazz) {
        Field[] fieldArray = clazz.getDeclaredFields();
        for (Field field : fieldArray) {
            Object value = ReflectUtils.invokeGetterMethod(updateObj, field.getName());
            boolean isUpdate = Objects.nonNull(value);
            if (isUpdate) {
                ReflectUtils.invokeSetterMethod(oldObj, field.getName(), value);
            }
        }

        @SuppressWarnings("unchecked")
        T t = (T) oldObj;
        return t;
    }

    public static boolean isNotPrimitive(Class<?> clazz) {
        return !isPrimitive(clazz);
    }

    public static boolean isPrimitive(Class<?> clazz) {
        return isBaseType(clazz) || isWrapBaseType(clazz);
    }

    /**
     * 判断是否是基础数据类型，即 int,double,long等类似格式
     */
    public static boolean isBaseType(Class<?> clazz) {
        return clazz.isPrimitive();
    }

    /**
     * 判断是否是基础数据类型的包装类型
     */
    public static boolean isWrapBaseType(Class<?> clz) {
        try {
            return ((Class<?>) clz.getField("TYPE").get(null)).isPrimitive();
        } catch (Exception e) {
            return false;
        }
    }

    public static Object getPrivateValue(Object obj, String name) {
        try {
            // /通过类的字节码得到该类中声明的所有属性，无论私有或公有
            Field field = obj.getClass().getDeclaredField(name);
            // 设置访问权限
            field.setAccessible(true);
            // 得到私有的变量值
            return field.get(obj);
        } catch (Exception e) {
            LOG.error("Fail to get private value", e);
            return null;
        }
    }

    public static Set<Class<?>> getInheritClass(Object obj) {
        if (Objects.isNull(obj)) {
            return Collections.emptySet();
        }

        Set<Class<?>> result = new HashSet<>();

        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            result.add(clazz);
            clazz = clazz.getSuperclass();
        }

        return result;
    }

    public static List<Field> getFieldFromClassSet(Set<Class<?>> classSet) {
        if (CollectionUtils.isEmpty(classSet)) {
            return Collections.emptyList();
        }
        return classSet.stream()
                .map(Class::getDeclaredFields)
                .flatMap(Arrays::stream)
                .collect(Collectors.toList());
    }

    /**
     * 初始化对象
     */
    public static <T> void initializeBean(T t) {
        Class<?> clazz = t.getClass();
        Field[] fieldArr = clazz.getDeclaredFields();
        for (Field field : fieldArr) {
            if (field.getType() == Integer.class) {
                invokeSetterMethod(t, field.getName(), 0);
            } else if (field.getType() == Long.class) {
                invokeSetterMethod(t, field.getName(), 0L);
            } else if (field.getType() == Short.class) {
                invokeSetterMethod(t, field.getName(), 0);
            } else if (field.getType() == Boolean.class) {
                invokeSetterMethod(t, field.getName(), Boolean.FALSE);
            } else if (field.getType() == Float.class) {
                invokeSetterMethod(t, field.getName(), 0f);
            } else if (field.getType() == Double.class) {
                invokeSetterMethod(t, field.getName(), 0f);
            } else if (field.getType() == String.class) {
                invokeSetterMethod(t, field.getName(), "");
            } else if (field.getType() == BigDecimal.class) {
                invokeSetterMethod(t, field.getName(), BigDecimal.ZERO);
            } else if (field.getType() == BigInteger.class) {
                invokeSetterMethod(t, field.getName(), BigInteger.ZERO);
            } else {
                LOG.info("初始化默认值类型不匹配,{},{}", t, field.getType());
            }
        }
    }

    public static String getCurrentMethodName() {
        try {
            return Thread.currentThread().getStackTrace()[2].getMethodName();
        } catch (Exception ex) {
            LOG.error("Fail to get current methodName", ex);
            return GeneratorUtils.nextUUID();
        }
    }

    public static String getCurrentWholeMethodName() {
        try {
            String className = Thread.currentThread().getStackTrace()[2].getClassName();
            String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
            return String.format("%s.%s", className, methodName);
        } catch (Exception ex) {
            LOG.error("Fail to get current whole methodName", ex);
            return GeneratorUtils.nextUUID();
        }
    }

    public static String getCurrentClassName() {
        try {
            return Thread.currentThread().getStackTrace()[2].getClassName();
        } catch (Exception ex) {
            LOG.error("Fail to get current className", ex);
            return GeneratorUtils.nextUUID();
        }
    }

    /**
     * 首字母大写
     */
    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * 首字母小写
     */
    private static String decapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

}
