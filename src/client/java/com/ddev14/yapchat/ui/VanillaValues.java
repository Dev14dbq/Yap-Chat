package com.ddev14.yapchat.ui;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Calls methods of vanilla option value sets by name. Their interfaces
 * (OptionInstance.SliderableValueSet / CycleableValueSet) are public only on newer
 * Minecraft versions, so the screen does not reference them directly.
 */
final class VanillaValues {
	private VanillaValues() {}

	/** Finds a method by name on the object's class, its superclasses and all interfaces; null if absent. */
	static Method method(Object target, String name) {
		if (target == null) return null;
		Deque<Class<?>> todo = new ArrayDeque<>();
		todo.add(target.getClass());
		while (!todo.isEmpty()) {
			Class<?> c = todo.poll();
			for (Method m : c.getDeclaredMethods()) {
				if (m.getName().equals(name) && !m.isSynthetic()) {
					m.setAccessible(true);
					return m;
				}
			}
			if (c.getSuperclass() != null) todo.add(c.getSuperclass());
			for (Class<?> i : c.getInterfaces()) todo.add(i);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	static <T> T call(Object target, String name, Object... args) {
		try {
			return (T) method(target, name).invoke(target, args);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Cannot call " + name + " on " + target, e);
		}
	}
}
