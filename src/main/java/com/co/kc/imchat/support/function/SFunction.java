package com.co.kc.imchat.support.function;

import java.io.Serializable;
import java.util.function.Function;

/**
 * Lambda表达式序列化要求函数式接口实现Serializable接口，
 * Lambda表达式序列化后真实存在的类为java.lang.invoke.SerializedLambda，
 * 一个Lambda表达式有一个看不见的方法，方法名为writeReplace，而这个方法的返回值就是SerializedLambda。
 * <p>
 * 继承Serializable接口, 这样SFunction的实现类也就实现了Serializable接口
 * <p>
 * <span>@FunctionalInterface</span>注解的作用:
 * 1. 让语义更清晰：声明接口用于Lambda。
 * 2. 编译器强校验：如果加了注解但接口有多个抽象方法，编译器会报错。
 *
 * @author kc
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {
}
