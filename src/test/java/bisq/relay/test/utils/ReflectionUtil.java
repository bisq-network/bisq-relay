/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.relay.test.utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A utility class that allows for accessing private methods and fields of classes via reflection.
 */
public class ReflectionUtil {
    private ReflectionUtil() {
        throw new AssertionError("This class must not be instantiated");
    }

    public static Method getPrivateMethod(
            @Nonnull final Class<?> clazz,
            @Nonnull final String methodName,
            @Nullable final Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    public static Field getPrivateField(
            @Nonnull final Class<?> clazz,
            @Nonnull final String fieldName) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }
}
