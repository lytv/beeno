/**
 * Copyright 2008 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package meetup.beeno.filter;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.filter.Filter;

/**
 * This filter is used to filter based on the value of a given column. It takes
 * an operator (equal, greater, not equal, etc) and either a byte [] value or a
 * byte [] comparator. If we have a byte [] value then we just do a
 * lexicographic compare. If this is not sufficient (eg you want to deserialize
 * a long and then compare it to a fixed long value, then you can pass in your
 * own comparator instead.
 */
public class ColumnMatchFilter implements Filter {

	/** Comparison operators. */
	public enum CompareOp {
		/** less than */
		LESS,
		/** less than or equal to */
		LESS_OR_EQUAL,
		/** equals */
		EQUAL,
		/** not equal */
		NOT_EQUAL,
		/** greater than or equal to */
		GREATER_OR_EQUAL,
		/** greater than */
		GREATER;
	}

	private byte[] columnName;
	private CompareOp compareOp;
	private byte[] value;
	private boolean filterIfColumnMissing;
	private boolean columnSeen = false;
	private boolean columnFiltered = false;

	public ColumnMatchFilter() {
		// for Writable
	}

	/**
	 * Constructor.
	 * 
	 * @param columnName
	 *            name of column
	 * @param compareOp
	 *            operator
	 * @param value
	 *            value to compare column values against
	 */
	public ColumnMatchFilter( final byte[] columnName, final CompareOp compareOp,
			final byte[] value ) {
		this(columnName, compareOp, value, true);
	}

	/**
	 * Constructor.
	 * 
	 * @param columnName
	 *            name of column
	 * @param compareOp
	 *            operator
	 * @param value
	 *            value to compare column values against
	 * @param filterIfColumnMissing
	 *            if true then we will filter rows that don't have the column.
	 */
	public ColumnMatchFilter( final byte[] columnName, final CompareOp compareOp,
			final byte[] value, boolean filterIfColumnMissing ) {
		this.columnName = columnName;
		this.compareOp = compareOp;
		this.value = value;
		this.filterIfColumnMissing = filterIfColumnMissing;
	}

	/**
	 * Constructor.
	 * 
	 * @param columnName
	 *            name of column
	 * @param compareOp
	 *            operator
	 * @param comparator
	 *            Comparator to use.
	 */
	public ColumnMatchFilter( final byte[] columnName, final CompareOp compareOp ) {
		this(columnName, compareOp, true);
	}

	/**
	 * Constructor.
	 * 
	 * @param columnName
	 *            name of column
	 * @param compareOp
	 *            operator
	 * @param comparator
	 *            Comparator to use.
	 * @param filterIfColumnMissing
	 *            if true then we will filter rows that don't have the column.
	 */
	public ColumnMatchFilter( final byte[] columnName, final CompareOp compareOp,
			boolean filterIfColumnMissing ) {
		this.columnName = columnName;
		this.compareOp = compareOp;
		this.filterIfColumnMissing = filterIfColumnMissing;
	}

	public boolean filterRowKey( final byte[] rowKey, int offset, int length ) {
		return false;
	}

	public Filter.ReturnCode filterKeyValue( KeyValue v ) {
		if (v.matchingColumn(this.columnName)) {
			byte[] val = v.getValue();
			if (val != null && val.length > 0)
				this.columnSeen = true;
			if (filterColumnValue(v.getValue())) {
				this.columnFiltered = true;
				return Filter.ReturnCode.NEXT_ROW;
			}
		}

		return Filter.ReturnCode.INCLUDE;
	}

	private boolean filterColumnValue( final byte[] data ) {
		int compareResult;
		compareResult = compare(value, data);

		switch (compareOp) {
		case LESS:
			return compareResult <= 0;
		case LESS_OR_EQUAL:
			return compareResult < 0;
		case EQUAL:
			return compareResult != 0;
		case NOT_EQUAL:
			return compareResult == 0;
		case GREATER_OR_EQUAL:
			return compareResult > 0;
		case GREATER:
			return compareResult >= 0;
		default:
			throw new RuntimeException("Unknown Compare op " + compareOp.name());
		}
	}

	public boolean filterAllRemaining() {
		return false;
	}

	public boolean filterRow() {
		if (columnFiltered)
			return true;
		
		if (filterIfColumnMissing && !columnSeen)
			return true;

		return false;
	}

	private int compare( final byte[] b1, final byte[] b2 ) {
		int len = Math.min(b1.length, b2.length);

		for (int i = 0; i < len; i++) {
			if (b1[i] != b2[i]) {
				return b1[i] - b2[i];
			}
		}
		return b1.length - b2.length;
	}

	public void reset() {
		this.columnSeen = false;
		this.columnFiltered = false;
	}

	public void readFields( final DataInput in ) throws IOException {
		int valueLen = in.readInt();
		if (valueLen > 0) {
			value = new byte[valueLen];
			in.readFully(value);
		}
		columnName = Bytes.readByteArray(in);
		compareOp = CompareOp.valueOf(in.readUTF());
		filterIfColumnMissing = in.readBoolean();
	}

	public void write( final DataOutput out ) throws IOException {
		if (value == null) {
			out.writeInt(0);
		}
		else {
			out.writeInt(value.length);
			out.write(value);
		}
		Bytes.writeByteArray(out, columnName);
		out.writeUTF(compareOp.name());
		out.writeBoolean(filterIfColumnMissing);
	}

}
