/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.repository.encryption;

import io.gravitee.am.model.common.Page;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Wraps a repository so the {@link EncryptedField} of its entity is encrypted on the way in and
 * decrypted on the way out, whatever method is called.
 * <p>
 * Arguments are encrypted on a copy, because some repositories return the instance they received:
 * encrypting it in place would hand ciphertext back to the caller. Results are decrypted in place,
 * since the repository builds a fresh instance for each read.
 *
 * @author GraviteeSource Team
 */
public final class EncryptingRepositoryProxy implements InvocationHandler {

    private final Object target;
    private final EncryptedField<?> field;
    private final FieldEncryptor encryptor;

    private EncryptingRepositoryProxy(Object target, EncryptedField<?> field, FieldEncryptor encryptor) {
        this.target = target;
        this.field = field;
        this.encryptor = encryptor;
    }

    public static Object wrap(Object target, EncryptedField<?> field, FieldEncryptor encryptor) {
        return Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                allInterfaces(target.getClass()),
                new EncryptingRepositoryProxy(target, field, encryptor));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, args);
        }
        Object[] effectiveArgs = encryptArguments(args);
        Object result;
        try {
            result = method.invoke(target, effectiveArgs);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        return decryptResult(result);
    }

    private Object invokeObjectMethod(Object proxy, Method method, Object[] args) throws Throwable {
        if ("equals".equals(method.getName())) {
            Object other = args[0];
            if (other != null && Proxy.isProxyClass(other.getClass())
                    && Proxy.getInvocationHandler(other) instanceof EncryptingRepositoryProxy handler) {
                other = handler.target;
            }
            return target.equals(other);
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Object[] encryptArguments(Object[] args) {
        if (args == null) {
            return null;
        }
        Object[] encrypted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            encrypted[i] = field.encryptedCopy(args[i], encryptor);
        }
        return encrypted;
    }

    @SuppressWarnings("unchecked")
    private Object decryptResult(Object result) {
        if (result instanceof Single<?> single) {
            return ((Single<Object>) single).map(this::decrypt);
        }
        if (result instanceof Maybe<?> maybe) {
            return ((Maybe<Object>) maybe).map(this::decrypt);
        }
        if (result instanceof Flowable<?> flowable) {
            return ((Flowable<Object>) flowable).map(this::decrypt);
        }
        if (result instanceof Observable<?> observable) {
            return ((Observable<Object>) observable).map(this::decrypt);
        }
        return decrypt(result);
    }

    private Object decrypt(Object value) {
        if (value instanceof Page<?> page) {
            page.getData().forEach(this::decrypt);
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(this::decrypt);
        } else {
            field.decryptInPlace(value, encryptor);
        }
        return value;
    }

    private static Class<?>[] allInterfaces(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            collectInterfaces(current, interfaces);
        }
        return interfaces.toArray(Class<?>[]::new);
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> interfaces) {
        for (Class<?> implemented : type.getInterfaces()) {
            if (interfaces.add(implemented)) {
                collectInterfaces(implemented, interfaces);
            }
        }
    }
}
