/*******************************************************************************
 * This file is part of Pebble.
 * 
 * Original work Copyright (c) 2009-2013 by the Twig Team
 * Modified work Copyright (c) 2013 by Mitchell Bösecke
 * 
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 ******************************************************************************/
package com.mitchellbosecke.pebble.template;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.AttributeNotFoundException;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.filter.Filter;
import com.mitchellbosecke.pebble.node.NodeMacro;
import com.mitchellbosecke.pebble.node.expression.NodeExpressionGetAttributeOrMethod;
import com.mitchellbosecke.pebble.test.Test;

public abstract class AbstractPebbleTemplate implements PebbleTemplate {

	private String sourceCode;
	protected StringBuilder builder = new StringBuilder();
	protected Map<String, Object> context;
	protected PebbleEngine engine;
	
	/*
	 * These are variables used to help with for loops
	 */
	protected Map<String, Object> currentLoop;
	protected int currentLoopLength;
	protected Iterator<?> currentLoopIterator;

	public abstract void buildContent() throws PebbleException;

	@Override
	public String render() throws PebbleException {
		return render(new HashMap<String, Object>());
	}

	@Override
	public String render(Map<String, Object> context) throws PebbleException {
		this.context = context;
		this.builder = new StringBuilder();
		buildContent();
		return builder.toString();
	}

	@Override
	public void setEngine(PebbleEngine engine) {
		this.engine = engine;
	}

	protected Object getAttribute(NodeExpressionGetAttributeOrMethod.Type type, Object object, String attribute)
			throws AttributeNotFoundException {
		return getAttribute(type, object, attribute, new Object[0]);
	}

	protected Object getAttribute(NodeExpressionGetAttributeOrMethod.Type type, Object object, String attribute,
			Object... args) throws AttributeNotFoundException {

		if (object == null) {
			throw new NullPointerException(String.format("Can not get attribute [%s] of null object.", attribute));
		}

		// hold onto original name for error reporting
		String originalAttributeName = attribute;

		Class<?> clazz = object.getClass();

		Object result = null;
		boolean found = false;

		if (!NodeExpressionGetAttributeOrMethod.Type.METHOD.equals(type)) {

			// is object a hash map?
			if (object instanceof Map && ((Map<?, ?>) object).containsKey(attribute)) {
				result = ((Map<?, ?>) object).get(attribute);
				found = true;
			}

			// check for public field
			try {
				Field field = clazz.getField(attribute);
				result = field.get(object);
				found = true;
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			}
		}

		if (result == null) {

			boolean passContextAsArgument = false;

			Method method = null;

			// check if attribute is a method
			try {
				method = clazz.getMethod(attribute);
				found = true;
			} catch (NoSuchMethodException | SecurityException e) {
			}

			// Check for macro
			if (method == null) {
				try {

					// in order to use reflection we need to know the EXACT
					// number and types of arguments the intended method takes
					List<Class<?>> paramTypes = new ArrayList<>();

					for (@SuppressWarnings("unused")
					Object param : args) {
						paramTypes.add(Object.class);
					}
					
					/*
					 * Add an extra parameter to account for the secret _context
					 * object that we will pass
					 */
					paramTypes.add(Object.class);

					method = clazz.getMethod(NodeMacro.MACRO_PREFIX + attribute,
							paramTypes.toArray(new Class[paramTypes.size()]));
					found = true;
					passContextAsArgument = true;
				} catch (NoSuchMethodException | SecurityException e) {
				}
			}

			// capitalize first letter of attribute for the following attempts
			attribute = Character.toUpperCase(attribute.charAt(0)) + attribute.substring(1);

			// check get method
			if (method == null) {
				try {
					method = clazz.getMethod("get" + attribute);
					found = true;
				} catch (NoSuchMethodException | SecurityException e) {
				}
			}

			// check is method
			if (method == null) {
				try {
					method = clazz.getMethod("is" + attribute);
					found = true;
				} catch (NoSuchMethodException | SecurityException e) {
				}
			}

			if (method != null) {
				try {
					if (passContextAsArgument) {
						List<Object> arguments = new ArrayList<>(Arrays.asList(args));
						arguments.add(context);
						args = arguments.toArray();
					}
					if (args.length > 0) {
						result = method.invoke(object, args);
					} else {
						result = method.invoke(object);
					}
					found = true;
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					if (e instanceof InvocationTargetException) {
						((InvocationTargetException) e).getTargetException().printStackTrace();
					}
				}
			}
		}

		if (!found) {
			throw new AttributeNotFoundException(String.format(
					"Attribute [%s] of [%s] does not exist or can not be accessed.", originalAttributeName, clazz));
		}
		return result;

	}

	public Object applyFilter(String filterName, Object... args) {
		List<Object> arguments = new ArrayList<>();

		Collections.addAll(arguments, args);

		Map<String, Filter> filters = engine.getFilters();
		Filter filter = filters.get(filterName);
		return filter.apply(arguments);
	}

	public boolean applyTest(String testName, Object... args) {
		ArrayList<Object> arguments = new ArrayList<>();

		// if args is null, it's because there was ONE argument and that
		// argument happens to be null
		if (args == null) {
			arguments.add(null);
		} else {
			Collections.addAll(arguments, args);
		}

		Map<String, Test> tests = engine.getTests();
		Test test = tests.get(testName);
		return test.apply(arguments);
	}

	public void setSourceCode(String source) {
		this.sourceCode = source;
	}

	public String getSourceCode() {
		return sourceCode;
	}

}
