package biweekly.io.json;

import static biweekly.util.StringUtils.NEWLINE;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import biweekly.parameter.ICalParameters;
import biweekly.parameter.Value;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;

/*
 Copyright (c) 2013, Michael Angstadt
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met: 

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer. 
 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution. 

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Writes data to an iCalendar JSON data stream (jCal).
 * @author Michael Angstadt
 * @see <a href="http://tools.ietf.org/html/draft-ietf-jcardcal-jcal-05">jCal
 * draft</a>
 */
public class JCalRawWriter implements Closeable {
	private final Writer writer;
	private final boolean wrapInArray;
	private final LinkedList<Info> stack = new LinkedList<Info>();
	private JsonGenerator jg;
	private boolean indent = false;
	private boolean componentEnded = false;

	/**
	 * Creates a new raw writer.
	 * @param writer the writer to the data stream
	 * @param wrapInArray true to wrap everything in an array, false not to
	 * (useful when writing more than one iCalendar object)
	 */
	public JCalRawWriter(Writer writer, boolean wrapInArray) {
		this.writer = writer;
		this.wrapInArray = wrapInArray;
	}

	/**
	 * Gets whether or not the JSON will be pretty-printed.
	 * @return true if it will be pretty-printed, false if not (defaults to
	 * false)
	 */
	public boolean isIndent() {
		return indent;
	}

	/**
	 * Sets whether or not to pretty-print the JSON.
	 * @param indent true to pretty-print it, false not to (defaults to false)
	 */
	public void setIndent(boolean indent) {
		this.indent = indent;
	}

	/**
	 * Writes the beginning of a new component array.
	 * @param componentName the component name (e.g. "vevent")
	 * @throws IOException if there's an I/O problem
	 */
	public void writeStartComponent(String componentName) throws IOException {
		if (jg == null) {
			init();
		}

		componentEnded = false;

		if (!stack.isEmpty()) {
			Info parent = stack.getLast();
			if (!parent.wroteEndPropertiesArray) {
				jg.writeEndArray();
				parent.wroteEndPropertiesArray = true;
			}
			if (!parent.wroteStartSubComponentsArray) {
				jg.writeStartArray();
				parent.wroteStartSubComponentsArray = true;
			}
		}

		jg.writeStartArray();
		indent(stack.size() * 2);
		jg.writeString(componentName);
		jg.writeStartArray(); //start properties array

		stack.add(new Info());
	}

	/**
	 * Closes the current component array.
	 * @throws IllegalStateException if there are no open components (
	 * {@link #writeStartComponent(String)} must be called first)
	 * @throws IOException if there's an I/O problem
	 */
	public void writeEndComponent() throws IOException {
		if (stack.isEmpty()) {
			throw new IllegalStateException("Call \"writeStartComponent\" first.");
		}
		Info cur = stack.removeLast();

		if (!cur.wroteEndPropertiesArray) {
			jg.writeEndArray();
		}
		if (!cur.wroteStartSubComponentsArray) {
			jg.writeStartArray();
		}

		jg.writeEndArray(); //end sub-components array
		jg.writeEndArray(); //end the array of this component

		componentEnded = true;
	}

	/**
	 * Writes a property to the current component.
	 * @param propertyName the property name (e.g. "version")
	 * @param value the property value
	 * @throws IllegalStateException if there are no open components (
	 * {@link #writeStartComponent(String)} must be called first) or if the last
	 * method called was {@link #writeEndComponent()}.
	 * @throws IOException if there's an I/O problem
	 */
	public void writeProperty(String propertyName, JCalValue value) throws IOException {
		writeProperty(propertyName, new ICalParameters(), value);
	}

	/**
	 * Writes a property to the current component.
	 * @param propertyName the property name (e.g. "version")
	 * @param parameters the parameters
	 * @param value the property value
	 * @throws IllegalStateException if there are no open components (
	 * {@link #writeStartComponent(String)} must be called first) or if the last
	 * method called was {@link #writeEndComponent()}.
	 * @throws IOException if there's an I/O problem
	 */
	public void writeProperty(String propertyName, ICalParameters parameters, JCalValue value) throws IOException {
		if (stack.isEmpty()) {
			throw new IllegalStateException("Call \"writeStartComponent\" first.");
		}
		if (componentEnded) {
			throw new IllegalStateException("Cannot write a property after calling \"writeEndComponent\".");
		}

		jg.writeStartArray();
		indent(stack.size() * 2);

		//write the property name
		jg.writeString(propertyName);

		//write parameters
		jg.writeStartObject();
		for (Map.Entry<String, List<String>> entry : parameters) {
			String name = entry.getKey().toLowerCase();
			List<String> values = entry.getValue();
			if (values.isEmpty()) {
				continue;
			}

			if (values.size() == 1) {
				jg.writeStringField(name, values.get(0));
			} else {
				jg.writeArrayFieldStart(name);
				for (String paramValue : values) {
					jg.writeString(paramValue);
				}
				jg.writeEndArray();
			}
		}
		jg.writeEndObject();

		//write data type
		Value dataType = value.getDataType();
		jg.writeString((dataType == null) ? "unknown" : dataType.getValue().toLowerCase());

		//write value
		for (JsonValue jsonValue : value.getValues()) {
			writeValue(jsonValue);
		}

		jg.writeEndArray();
	}

	private void writeValue(JsonValue jsonValue) throws IOException {
		if (jsonValue.isNull()) {
			jg.writeNull();
			return;
		}

		Object val = jsonValue.getValue();
		if (val != null) {
			if (val instanceof Byte) {
				jg.writeNumber((Byte) val);
			} else if (val instanceof Short) {
				jg.writeNumber((Short) val);
			} else if (val instanceof Integer) {
				jg.writeNumber((Integer) val);
			} else if (val instanceof Long) {
				jg.writeNumber((Long) val);
			} else if (val instanceof Float) {
				jg.writeNumber((Float) val);
			} else if (val instanceof Double) {
				jg.writeNumber((Double) val);
			} else if (val instanceof Boolean) {
				jg.writeBoolean((Boolean) val);
			} else {
				jg.writeString(val.toString());
			}
			return;
		}

		List<JsonValue> array = jsonValue.getArray();
		if (array != null) {
			jg.writeStartArray();
			for (JsonValue element : array) {
				writeValue(element);
			}
			jg.writeEndArray();
			return;
		}

		Map<String, JsonValue> object = jsonValue.getObject();
		if (object != null) {
			jg.writeStartObject();
			for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
				jg.writeFieldName(entry.getKey());
				writeValue(entry.getValue());
			}
			jg.writeEndObject();
			return;
		}
	}

	/**
	 * Checks to see if pretty-printing is enabled, and adds indentation
	 * whitespace if it is.
	 * @param spaces the number of spaces to indent with
	 * @throws IOException
	 */
	private void indent(int spaces) throws IOException {
		if (indent) {
			jg.writeRaw(NEWLINE);
			for (int i = 0; i < spaces; i++) {
				jg.writeRaw(' ');
			}
		}
	}

	/**
	 * Finishes writing the JSON document so that it is syntactically correct.
	 * No more data can be written once this method is called.
	 * @throws IOException if there's a problem closing the stream
	 */
	public void closeJsonStream() throws IOException {
		if (jg == null) {
			return;
		}

		while (!stack.isEmpty()) {
			writeEndComponent();
		}

		if (wrapInArray) {
			indent(0);
			jg.writeEndArray();
		}

		jg.close();
	}

	/**
	 * Finishes writing the JSON document and closes the underlying
	 * {@link Writer}.
	 * @throws IOException if there's a problem closing the stream
	 */
	public void close() throws IOException {
		if (jg == null) {
			return;
		}

		closeJsonStream();
		writer.close();
	}

	private void init() throws IOException {
		JsonFactory factory = new JsonFactory();
		factory.configure(Feature.AUTO_CLOSE_TARGET, false);
		jg = factory.createJsonGenerator(writer);

		if (wrapInArray) {
			jg.writeStartArray();
			indent(0);
		}
	}

	private static class Info {
		public boolean wroteEndPropertiesArray = false;
		public boolean wroteStartSubComponentsArray = false;
	}
}
