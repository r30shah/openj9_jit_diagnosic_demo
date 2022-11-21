/*******************************************************************************
 * Copyright (c) 1998, 2020 IBM Corp. and others
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] http://openjdk.java.net/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0 OR LicenseRef-GPL-2.0 WITH Assembly-exception
 *******************************************************************************/


import static org.junit.Assert.*;
import java.io.UnsupportedEncodingException;
import org.junit.Test;
import org.junit.Assert;



@SuppressWarnings("unused")
public class Test_String {
	String hw1 = "HelloWorld";
	String hw2 = "HelloWorld";
	String hwlc = "helloworld";
	String hwuc = "HELLOWORLD";
	String hello1 = "Hello";
	String world1 = "World";
	String comp11 = "Test String";
	String split1 = "boo:and:foo";
	Object obj = new Object();
	char[] buf = { 'W', 'o', 'r', 'l', 'd' };
	char[] rbuf = new char[5];

	/**
	 * @tests java.lang.String#String()
	 */
	@Test
	public void test_Constructor() {
		assertTrue("Created incorrect string", new String().equals(""));
	}

	/**
	 * @tests java.lang.String#String(byte[])
	 */
	@Test
	public void test_Constructor2() {
		assertTrue("Failed to create string", new String(hw1.getBytes()).equals(hw1));
	}

	/**
	 * @tests java.lang.String#String(byte[], int)
	 */
	@Test
	public void test_Constructor3() {
		String s = new String(new byte[] { 65, 66, 67, 68, 69 }, 0);
		assertTrue("Incorrect string returned: " + s, s.equals("ABCDE"));
		s = new String(new byte[] { 65, 66, 67, 68, 69 }, 1);
		assertTrue("Did not use nonzero hibyte", !s.equals("ABCDE"));
	}

	/**
	 * @tests java.lang.String#String(byte[], int, int)
	 */
	@Test
	public void test_Constructor4() {
		assertTrue("Failed to create string",
				new String(hw1.getBytes(), 0, hw1.getBytes().length).equals(hw1));

		boolean exception = false;
		try {
			new String(new byte[0], 0, Integer.MAX_VALUE);
		} catch (IndexOutOfBoundsException e) {
			exception = true;
		}
		assertTrue("Did not throw exception", exception);
	}

	/**
	 * @tests java.lang.String#String(byte[], int, int, int)
	 */
	@Test
	public void test_Constructor5() {
		String s = new String(new byte[] { 65, 66, 67, 68, 69 }, 0, 1, 3);
		assertTrue("Incorrect string returned: " + s, s.equals("BCD"));
		s = new String(new byte[] { 65, 66, 67, 68, 69 }, 1, 0, 5);
		assertTrue("Did not use nonzero hibyte", !s.equals("ABCDE"));
	}

	/**
	 * @tests java.lang.String#String(byte[], int, int, java.lang.String)
	 */
	@Test
	public void test_Constructor6() {
		String s = null;
		try {
			s = new String(new byte[] { 65, 66, 67, 68, 69 }, 0, 5, "8859_1");
		} catch (Exception e) {
			assertTrue("Threw exception: " + e.toString(), false);
		}
		assertTrue("Incorrect string returned: " + s, s.equals("ABCDE"));
	}

	/**
	 * @tests java.lang.String#String(byte[], java.lang.String)
	 */
	@Test
	public void test_Constructor7() {
		String s = null;
		try {
			s = new String(new byte[] { 65, 66, 67, 68, 69 }, "8859_1");
		} catch (Exception e) {
			assertTrue("Threw exception: " + e.toString(), false);
		}
		assertTrue("Incorrect string returned: " + s, s.equals("ABCDE"));
	}

	/**
	 * @tests java.lang.String#String(byte[], java.lang.String)
	 */
	@Test
	public void test_Constructor8() {
		/* [PR 122454] Count invalid GB18030 sequences correctly */
		try {
			new String(new byte[] { (byte)0x90, (byte)0x30, 0, 0 }, "GB18030");
		} catch (UnsupportedEncodingException e) {
			// passed
		} catch (Exception e) {
			Assert.fail("Unexpected: " + e);
		}
	}

	/**
	 * @tests java.lang.String#String(char[])
	 */
	@Test
	public void test_Constructor9() {
		assertTrue("Failed Constructor test", new String(buf).equals("World"));
	}

	/**
	 * @tests java.lang.String#String(char[], int, int)
	 */
	@Test
	public void test_Constructor10() {
		char[] buf1 = { 'H', 'e', 'l', 'l', 'o', 'W', 'o', 'r', 'l', 'd' };
		String s1 = new String(buf1, 0, buf1.length);
		assertTrue("Incorrect string created", hw1.equals(s1));

		boolean exception = false;
		try {
			new String(new char[0], 0, Integer.MAX_VALUE);
		} catch (IndexOutOfBoundsException e) {
			exception = true;
		}
		assertTrue("Did not throw exception", exception);

		char[] buf2 = { '\u201c', 'X', '\u201d', '\n', '\u201c', 'X', '\u201d' };
		String s2 = new String(buf2, 4, 3);
		assertTrue("Incorrect string created", s2.equals("\u201cX\u201d"));
	}

	/**
	 * @tests java.lang.String#String(java.lang.String)
	 */
	@Test
	public void test_Constructor11() {
		String s = new String("Hello World");
		assertTrue("Failed to construct correct string", s.equals("Hello World"));
	}

	/**
	 * @tests java.lang.String#String(java.lang.String)
	 */
	@Test
	public void test_Constructor12() {
		// substring
		String s = new String("Hello World".substring(0));
		assertTrue("Failed to construct correct string", s.equals("Hello World"));
	}

	/**
	 * @tests java.lang.String#String(java.lang.String)
	 */
	@Test
	public void test_Constructor13() {
		// substring length_0
		String s = new String("Hello World".substring(4, 4));
		assertTrue("Failed to construct correct string when the argument is a substring of length 0",
				s.equals(""));
	}

	/**
	 * @tests java.lang.String#String(java.lang.String)
	 */
	@Test
	public void test_Constructor14() {
		// substring_length_1_range
		char[] chars = new char[256];
		for (int i = 0; i < chars.length; i++) {
			chars[i] = (char)i;
		}
		String string = new String(chars);
		for (int i = 0; i < chars.length; i++) {
			String s = new String(string.substring(i, i + 1));
			assertTrue(
					"Failed to construct correct string when i is " + i + ", expected: " + chars[i] + "; but got: " + s,
					s.equals("" + chars[i]));
		}
	}

	/**
	 * @tests java.lang.String#String(java.lang.String)
	 */
	@Test
	public void test_Constructor15() {
		// substring_length_1
		String s = new String(("Hello World" + "\u0090").substring(11, 12));
		assertTrue(
				"Failed to construct correct string when the argument is a susbtring of legnth 1 and is out the range of ascii",
				s.equals("\u0090"));
	}

	/**
	 * @tests java.lang.String#String(java.lang.String)
	 */
	@Test
	public void test_Constructor16() {
		// substring_length_normal
		String s = new String("Hello World".substring(2));
		assertTrue("Failed to construct correct string when the argument is a substring",
				s.equals("llo World"));
	}

	/**
	 * @tests java.lang.String#String(java.lang.StringBuffer)
	 */
	@Test
	public void test_Constructor17() {
		StringBuffer sb = new StringBuffer();
		sb.append("HelloWorld");
		assertTrue("Created incorrect string", new String(sb).equals("HelloWorld"));
	}


	/**
	 * @tests java.lang.String#String(int[], int, int)
	 */
	@Test
	public void test_Constructor18() {
		String s1 = new String(new int[] { 65, 66, 67, 68 }, 0, 4);
		assertTrue("Invalid start=0", s1.equals("ABCD"));
		s1 = new String(new int[] { 65, 66, 67, 68 }, 1, 3);
		assertTrue("Invalid start=1", s1.equals("BCD"));
		s1 = new String(new int[] { 65, 66, 67, 68 }, 1, 2);
		assertTrue("Invalid start=1,length=2", s1.equals("BC"));
		s1 = new String(new int[] { 65, 66, 0x10000, 68, 69 }, 1, 3);
		assertTrue("Invalid codePoint 0x10000", s1.equals("B\ud800\udc00D"));
		s1 = new String(new int[] { 65, 66, 0x10400, 68 }, 1, 3);
		assertTrue("Invalid codePoint 0x10400", s1.equals("B\ud801\udc00D"));
		s1 = new String(new int[] { 65, 66, 0x10ffff, 68 }, 1, 3);
		assertTrue("Invalid codePoint 0x10ffff", s1.equals("B\udbff\udfffD"));
	}
}
