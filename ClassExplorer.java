import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class ClassExplorer
{
	private static final Hashtable<Integer, String> constant_pool = new Hashtable<Integer, String>();

	private static void dump_bytes (ByteBuffer in, PrintWriter out, int len) throws Exception
	{
		for (int i = 0; i < len; i ++) {
			if (i % 32 == 0) out.print("b ");
			out.printf("%02x", in.get() & 0xff);
			if (i % 32 == 31 || i == len - 1)
				out.println();
		}
	}

	private static void dump_bytes (byte [] bytes, PrintWriter out) throws Exception
	{
		for (int i = 0; i < bytes.length; i ++) {
			if (i % 32 == 0) out.print("b ");
			out.printf("%02x", bytes[i] & 0xff);
			if (i % 32 == 31 || i == bytes.length - 1)
				out.println();
		}
	}

	private static void dump_code (byte [] code, PrintWriter out) throws Exception
	{
		dump_bytes(code, out);
	}

	private static void dump_attribute_info (ByteBuffer in, PrintWriter out) throws Exception
	{
		int nameid = in.getShort() & 0xffff;
		String name = constant_pool.get(nameid);
		int len = in.getInt();
		assert len >= 0;
		int end = in.position() + len;
		out.println("u2 " + nameid + " # " + (name == null ? "unknown attribute" : name));
		out.println("u4 " + len);

		if ("ConstantValue".equals(name)) {
			out.println("u2 " + (in.getShort() & 0xffff));
		} else if ("Code".equals(name)) {
			out.println("u2 " + (in.getShort() & 0xffff) + " # attribute_name_index");
			out.println("u4 " + in.getInt() + " # attribute_length");
			out.println("u2 " + (in.getShort() & 0xffff) + " # max_stack");
			out.println("u2 " + (in.getShort() & 0xffff) + " # max_locals");

			int code_length = in.getInt();
			assert code_length >= 0;
			out.println("u4 " + code_length + " # code_length");
			byte [] code = new byte [code_length];
			in.get(code);
			dump_code(code, out);

			int exception_table_length = in.getShort() & 0xffff;
			out.println("u2 " + exception_table_length + " # exception_table_length");
			for (int i = 0; i < exception_table_length; i ++) {
				out.print("u2a " + (in.getShort() & 0xffff));
				out.print("," + (in.getShort() & 0xffff));
				out.print("," + (in.getShort() & 0xffff));
				out.println("," + (in.getShort() & 0xffff));
			}

			int attributes_count = in.getShort() & 0xffff;
			out.println("u2 " + attributes_count + " # attributes_count");
			for (int i = 0; i < attributes_count; i ++)
				dump_attribute_info(in, out);
		} else if ("Signature".equals(name) || "SourceFile".equals(name)) {
			int cpidx = in.getShort() & 0xffff;
			String str = constant_pool.get(cpidx);
			out.println("u2 " + cpidx + (str == null ? "" : " # " + str));
		} else {
			dump_bytes(in, out, len);
		}
		assert in.position() == end;
	}

	private static void dump_fieldmethod_info (ByteBuffer in, PrintWriter out) throws Exception
	{
		for (String key : new String[]{"access_flags", "name_index", "descriptor_index"})
			out.println("u2 " + (in.getShort() & 0xffff) + " # " + key);
		int attributes_count = in.getShort() & 0xffff;
		out.println("u2 " + attributes_count + " # attributes_count");
		for (int i = 0; i < attributes_count; i ++)
			dump_attribute_info(in, out);
	}

	private static void dump_utf8 (ByteBuffer in, PrintWriter out, int cpindex) throws Exception
	{
		int len = in.getShort() & 0xffff;
		byte [] bytes = new byte [len];
		in.get(bytes);
		boolean ascii = true;
		boolean regular = true;
		for (int i = 0; i < len; i ++) {
			if ((bytes[i] & 0xff) > 0x7f)
				ascii = false;
			if ((bytes[i] & 0xff) < 0x20 || (bytes[i] & 0xff) > 0x7e)
				regular = false;
		}

		String str = ascii ? new String(bytes, java.nio.charset.StandardCharsets.US_ASCII) : null;
		if (str != null)
			constant_pool.put(cpindex, str);

		if (regular) {
			out.println("ascii " + str);
		} else {
			out.println("u2 " + len);
			dump_bytes(bytes, out);
		}
	}

	public static final String [] cp_names = new String [] {
		"CONSTANT_0","CONSTANT_Utf8","CONSTANT_2","CONSTANT_Integer","CONSTANT_Float",
		"CONSTANT_Long","CONSTANT_Double","CONSTANT_Class","CONSTANT_String","CONSTANT_Fieldref",
		"CONSTANT_Methodref","CONSTANT_InterfaceMethodref", "CONSTANT_NameAndType","CONSTANT_13","CONSTANT_14",
		"CONSTANT_MethodHandle","CONSTANT_MethodType","CONSTANT_17","CONSTANT_InvokeDynamic","CONSTANT_19"};
	private static void dump_cp_info (ByteBuffer in, PrintWriter out, int cpindex) throws Exception
	{
		int tag = in.get() & 0xff;
		out.println("u1 " + tag + " # " + cp_names[tag]);
		switch (tag) {
		case 7: case 8: case 16:
			out.println("u2 " + (in.getShort() & 0xffff));
			break;
		case 9: case 10: case 11: case 12: case 15: case 18:
			out.print("u2a " + (in.getShort() & 0xffff));
			out.println("," + (in.getShort() & 0xffff));
			break;
		case 3: case 4:
			out.println("u4 " + (in.getInt() & 0xffffffffl));
			break;
		case 5: case 6:
			out.print("u4a " + (in.getInt() & 0xffffffffl));
			out.println("," + (in.getInt() & 0xffffffffl));
			break;
		case 1:
			dump_utf8(in, out, cpindex);
			break;
		default:
			System.err.println("Unknown Constant Pool tag " + tag);
		}
	}

	private static void dump (String clspath, String txtpath) throws Exception
	{
		FileChannel inc = new FileInputStream(clspath).getChannel();
		ByteBuffer in = ByteBuffer.allocate((int)inc.size());
		inc.read(in);
		in.rewind();
		int magic = in.getInt();
		if (magic != 0xCAFEBABE) {
			System.err.println("Not a class file. wrong magic " + magic);
			return;
		}
		PrintWriter out = new PrintWriter(txtpath);
		int minor_version = in.getShort() & 0xffff;
		out.println("u2 " + minor_version + " # minor_version");
		int major_version = in.getShort() & 0xffff;
		out.println("u2 " + major_version + " # major_version");

		int constant_pool_count = in.getShort() & 0xffff;
		out.println("u2 " + constant_pool_count + " # constant_pool_count");
		for (int i = 1; i < constant_pool_count; i ++)
			dump_cp_info(in, out, i);

		for (String key : new String[]{"access_flags", "this_class", "super_class"})
			out.println("u2 " + (in.getShort() & 0xffff) + " # " + key);

		int interfaces_count = in.getShort() & 0xffff;
		out.println("u2 " + interfaces_count + " # interfaces_count");
		for (int i = 0; i < interfaces_count; i ++) {
			if (i % 20 == 0) out.print("u2a ");
			out.print(in.getShort() & 0xffff);
			if (i % 20 == 19 || i == interfaces_count - 1)
				out.println();
			else
				out.print(",");
		}

		int fields_count = in.getShort() & 0xffff;
		out.println("u2 " + fields_count + " # fields_count");
		for (int i = 0; i < fields_count; i ++)
			dump_fieldmethod_info(in, out);

		int methods_count = in.getShort() & 0xffff;
		out.println("u2 " + methods_count + " # methods_count");
		for (int i = 0; i < methods_count; i ++)
			dump_fieldmethod_info(in, out);

		int attributes_count = in.getShort() & 0xffff;
		out.println("u2 " + attributes_count + " # attributes_count");
		for (int i = 0; i < attributes_count; i ++)
			dump_attribute_info(in, out);
	}

	private static void build (String txtpath, String clspath) throws Exception
	{
	}

	private static void usage ()
	{
	}

	public static void main (String [] args) throws Exception
	{
		if (args.length == 3 && (args[0].equals("dump") || args[0].equals("d"))) {
			dump(args[1], args[2]);
		} else if (args.length == 2 && (args[0].equals("dump") || args[0].equals("d"))) {
			dump(args[1], args[1].replaceAll("\\.class$", ".classexp.txt"));
		} else if (args.length == 3 && (args[0].equals("build") || args[0].equals("b"))) {
			build(args[1], args[2]);
		} else if (args.length == 2 && (args[0].equals("build") || args[0].equals("b"))) {
			build(args[1], args[1].replaceAll("(.classexp)?.txt$", ".class"));
		} else {
			usage();
		}
	}
}
