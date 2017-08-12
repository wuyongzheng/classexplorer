import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class ClassExplorer
{
	private static final String [] indents = new String [] {
		"", " ", "  ", "   ", "    ",
		"     ", "      ", "       ", "        ", "         "};

	final private static char[] hexArray = "0123456789abcdef".toCharArray();
	public static String bytesToHex (byte [] bytes, int offset, int length)
	{
		char[] hexChars = new char[length * 2 ];
		for (int j = 0; j < length; j++) {
			int v = bytes[offset + j] & 0xff;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0xf];
		}
		return new String(hexChars);
	}
	public static String bytesToHex (byte [] bytes) { return bytesToHex(bytes, 0, bytes.length); }

	private static final Hashtable<Integer, String> constant_pool = new Hashtable<Integer, String>();

	private static void dump_bytes (ByteBuffer in, PrintWriter out, int len, int indent) throws Exception
	{
		for (int i = 0; i < len; i ++) {
			if (i % 32 == 0) out.print(indents[indent] + "b ");
			out.printf("%02x", in.get() & 0xff);
			if (i % 32 == 31 || i == len - 1)
				out.println();
		}
	}

	private static void dump_bytes (byte [] bytes, PrintWriter out, int indent) throws Exception
	{
		for (int i = 0; i < bytes.length; i ++) {
			if (i % 32 == 0) out.print(indents[indent] + "b ");
			out.printf("%02x", bytes[i] & 0xff);
			if (i % 32 == 31 || i == bytes.length - 1)
				out.println();
		}
	}

	private static int getIntFromBytes (byte high, byte low) { return ((high << 8) | (low & 0xff)) & 0xffff;}
	private static int getIntFromBytes (byte b0, byte b1, byte b2, byte b3)
	{
		return ((b0 & 0xff) << 24) |
			((b1 & 0xff) << 16) |
			((b2 & 0xff) << 8) |
			(b3 & 0xff);
	}

	/* Refer https://en.wikipedia.org/wiki/Java_bytecode_instruction_listings
	 *       https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-6.html */
	private static final byte [] instructionSize = new byte [] {
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,2,1,2,2,1,1,1,1,1,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,2,2,2,2,2,2,
		2,2,2,2,2,2,2,2,2,1,100,100,0,0,0,0,0,0,2,2,2,2,2,2,2,4,4,2,1,2,0,0,
		2,2,0,0,100,3,2,2,4,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
	private static final String [] instructionMnemonic = new String [] {
		"nop","aconst_null","iconst_m1","iconst_0","iconst_1","iconst_2","iconst_3","iconst_4",
		"iconst_5","lconst_0","lconst_1","fconst_0","fconst_1","fconst_2","dconst_0","dconst_1",
		"bipush","sipush","ldc","ldc_w","ldc2_w","iload","lload","fload",
		"dload","aload","iload_0","iload_1","iload_2","iload_3","lload_0","lload_1",
		"lload_2","lload_3","fload_0","fload_1","fload_2","fload_3","dload_0","dload_1",
		"dload_2","dload_3","aload_0","aload_1","aload_2","aload_3","iaload","laload",
		"faload","daload","aaload","baload","caload","saload","istore","lstore",
		"fstore","dstore","astore","istore_0","istore_1","istore_2","istore_3","lstore_0",
		"lstore_1","lstore_2","lstore_3","fstore_0","fstore_1","fstore_2","fstore_3","dstore_0",
		"dstore_1","dstore_2","dstore_3","astore_0","astore_1","astore_2","astore_3","iastore",
		"lastore","fastore","dastore","aastore","bastore","castore","sastore","pop",
		"pop2","dup","dup_x1","dup_x2","dup2","dup2_x1","dup2_x2","swap",
		"iadd","ladd","fadd","dadd","isub","lsub","fsub","dsub",
		"imul","lmul","fmul","dmul","idiv","ldiv","fdiv","ddiv",
		"irem","lrem","frem","drem","ineg","lneg","fneg","dneg",
		"ishl","lshl","ishr","lshr","iushr","lushr","iand","land",
		"ior","lor","ixor","lxor","iinc","i2l","i2f","i2d",
		"l2i","l2f","l2d","f2i","f2l","f2d","d2i","d2l",
		"d2f","i2b","i2c","i2s","lcmp","fcmpl","fcmpg","dcmpl",
		"dcmpg","ifeq","ifne","iflt","ifge","ifgt","ifle","if_icmpeq",
		"if_icmpne","if_icmplt","if_icmpge","if_icmpgt","if_icmple","if_acmpeq","if_acmpne","goto",
		"jsr","ret","tableswitch","lookupswitch","ireturn","lreturn","freturn","dreturn",
		"areturn","return","getstatic","putstatic","getfield","putfield","invokevirtual","invokespecial",
		"invokestatic","invokeinterface","invokedynamic","new","newarray","anewarray","arraylength","athrow",
		"checkcast","instanceof","monitorenter","monitorexit","wide","multianewarray","ifnull","ifnonnull",
		"goto_w","jsr_w","breakpoint","reserved_cb","reserved_cc","reserved_cd","reserved_ce","reserved_cf",
		"reserved_d0","reserved_d1","reserved_d2","reserved_d3","reserved_d4","reserved_d5","reserved_d6","reserved_d7",
		"reserved_d8","reserved_d9","reserved_da","reserved_db","reserved_dc","reserved_dd","reserved_de","reserved_df",
		"reserved_e0","reserved_e1","reserved_e2","reserved_e3","reserved_e4","reserved_e5","reserved_e6","reserved_e7",
		"reserved_e8","reserved_e9","reserved_ea","reserved_eb","reserved_ec","reserved_ed","reserved_ee","reserved_ef",
		"reserved_f0","reserved_f1","reserved_f2","reserved_f3","reserved_f4","reserved_f5","reserved_f6","reserved_f7",
		"reserved_f8","reserved_f9","reserved_fa","reserved_fb","reserved_fc","reserved_fd","impdep1","impdep2"};
	private static void dump_code (byte [] code, PrintWriter out, int indent) throws Exception
	{
		if (code.length == 0) return;

		out.println(indents[indent] + "# Begin code of " + code.length + " bytes");
		for (int i = 0; i < code.length; i ++) {
			int opcode = code[i] & 0xff;
			int size;
			if (opcode == 0xaa) { // tableswitch
				int j = (i + 4) / 4 * 4;
				int def = getIntFromBytes(code[j], code[j+1], code[j+2], code[j+3]); j += 4;
				int low = getIntFromBytes(code[j], code[j+1], code[j+2], code[j+3]); j += 4;
				int high = getIntFromBytes(code[j], code[j+1], code[j+2], code[j+3]); j += 4;
				assert low <= high;
				size = j + 4 * (high - low + 1) - i - 1;
				out.println(indents[indent] + "b " + bytesToHex(code, i, size + 1) + " # " +
						i + ": tableswitch " + def + ", " + low + ", " + high + " ...");
			} else if (opcode == 0xab) { // lookupswitch
				int j = (i + 4) / 4 * 4;
				int def = getIntFromBytes(code[j], code[j+1], code[j+2], code[j+3]); j += 4;
				int npairs = getIntFromBytes(code[j], code[j+1], code[j+2], code[j+3]); j += 4;
				assert npairs >= 0;
				size = j + 8 * npairs - i - 1;
				out.println(indents[indent] + "b " + bytesToHex(code, i, size + 1) + " # " +
						i + ": lookupswitch " + def + ", " + npairs + " ...");
			} else if (opcode == 0xc4) { // wide
				if ((code[i] & 0xff) == 0x84) { // iinc
					size = 5;
					out.println(indents[indent] + "b " + bytesToHex(code, i, size + 1) + " # " +
							i + ": wide iinc " +
							getIntFromBytes(code[i+2], code[i+3]) + ", " +
							getIntFromBytes(code[i+4], code[i+5]));
				} else {
					size = 3;
					out.println(indents[indent] + "b " + bytesToHex(code, i, size + 1) + " # " +
							i + ": wide " +
							instructionMnemonic[code[i+1]] + " " +
							getIntFromBytes(code[i+2], code[i+3]));
				}
			} else {
				size = instructionSize[opcode];
				out.println(indents[indent] + "b " + bytesToHex(code, i, size + 1) + " # " +
						i + ": " + instructionMnemonic[opcode]);
			}
			i += size;
		}
		out.println(indents[indent] + "# End code");
	}

	private static void dump_attribute_info (ByteBuffer in, PrintWriter out, int indent) throws Exception
	{
		int nameid = in.getShort() & 0xffff;
		String name = constant_pool.get(nameid);
		int len = in.getInt();
		assert len >= 0;
		int end = in.position() + len;
		out.println(indents[indent] + "u2 " + nameid + " # " + (name == null ? "unknown attribute" : name));
		out.println(indents[indent] + "u4 " + len);

		indent ++;
		if ("ConstantValue".equals(name)) {
			out.println(indents[indent] + "u2 " + (in.getShort() & 0xffff) + " # constantvalue_index");
		} else if ("Code".equals(name)) {
			out.println(indents[indent] + "u2 " + (in.getShort() & 0xffff) + " # max_stack");
			out.println(indents[indent] + "u2 " + (in.getShort() & 0xffff) + " # max_locals");

			int code_length = in.getInt();
			assert code_length >= 0;
			out.println(indents[indent] + "u4 " + code_length + " # code_length");
			byte [] code = new byte [code_length];
			in.get(code);
			dump_code(code, out, indent+1);

			int exception_table_length = in.getShort() & 0xffff;
			out.println(indents[indent] + "u2 " + exception_table_length + " # exception_table_length");
			for (int i = 0; i < exception_table_length; i ++) {
				out.print(indents[indent] + "u2 " + (in.getShort() & 0xffff));
				out.print("," + (in.getShort() & 0xffff));
				out.print("," + (in.getShort() & 0xffff));
				out.println("," + (in.getShort() & 0xffff));
			}

			int attributes_count = in.getShort() & 0xffff;
			out.println(indents[indent] + "u2 " + attributes_count + " # attributes_count");
			for (int i = 0; i < attributes_count; i ++)
				dump_attribute_info(in, out, indent);
		} else if ("Signature".equals(name) || "SourceFile".equals(name)) {
			int cpidx = in.getShort() & 0xffff;
			String str = constant_pool.get(cpidx);
			out.println(indents[indent] + "u2 " + cpidx + (str == null ? "" : " # " + str));
		} else {
			dump_bytes(in, out, len, indent);
		}
		assert in.position() == end;
	}

	private static void dump_fieldmethod_info (ByteBuffer in, PrintWriter out, boolean isMethod) throws Exception
	{
		int access_flags = in.getShort() & 0xffff;
		int name_index = in.getShort() & 0xffff;
		int descriptor_index = in.getShort() & 0xffff;
		String name = constant_pool.get(name_index) + " " + constant_pool.get(descriptor_index);
		out.println("# Begin " + (isMethod ? "method " : "field ") + name);
		out.println("u2 " + access_flags + " # access_flags");
		out.println("u2 " + name_index + " # name_index");
		out.println("u2 " + descriptor_index + " # descriptor_index");

		int attributes_count = in.getShort() & 0xffff;
		out.println("u2 " + attributes_count + " # attributes_count");
		for (int i = 0; i < attributes_count; i ++)
			dump_attribute_info(in, out, 0);
		out.println("# End " + (isMethod ? "method " : "field ") + name);
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
			dump_bytes(bytes, out, 0);
		}
	}

	public static final String [] cp_names = new String [] {
		"CONSTANT_0","CONSTANT_Utf8","CONSTANT_2","CONSTANT_Integer","CONSTANT_Float",
		"CONSTANT_Long","CONSTANT_Double","CONSTANT_Class","CONSTANT_String","CONSTANT_Fieldref",
		"CONSTANT_Methodref","CONSTANT_InterfaceMethodref", "CONSTANT_NameAndType","CONSTANT_13","CONSTANT_14",
		"CONSTANT_MethodHandle","CONSTANT_MethodType","CONSTANT_17","CONSTANT_InvokeDynamic","CONSTANT_19"};
	private static boolean dump_cp_info (ByteBuffer in, PrintWriter out, int cpindex) throws Exception
	{
		int tag = in.get() & 0xff;
		out.println("u1 " + tag + " # " + cpindex + ": " + cp_names[tag]);
		switch (tag) {
		case 7: case 8: case 16:
			out.println("u2 " + (in.getShort() & 0xffff));
			break;
		case 9: case 10: case 11: case 12: case 15: case 18:
			out.print("u2 " + (in.getShort() & 0xffff));
			out.println("," + (in.getShort() & 0xffff));
			break;
		case 3: case 4:
			out.println("u4 " + (in.getInt() & 0xffffffffl));
			break;
		case 5: case 6:
			out.print("u4 " + (in.getInt() & 0xffffffffl));
			out.println("," + (in.getInt() & 0xffffffffl));
			return true;
		case 1:
			dump_utf8(in, out, cpindex);
			break;
		default:
			System.err.println("Unknown Constant Pool tag " + tag);
		}
		return false;
	}

	private static void dump (String clspath, String txtpath) throws Exception
	{
		FileChannel inc = new FileInputStream(clspath).getChannel();
		ByteBuffer in = ByteBuffer.allocate((int)inc.size());
		inc.read(in);
		in.rewind();
		int magic = in.getInt();
		if (magic != 0xCAFEBABE) {
			System.err.printf("Not a class file. wrong magic %x\n", magic);
			return;
		}
		PrintWriter out = new PrintWriter(txtpath);
		out.println("b cafebabe # magic");
		int minor_version = in.getShort() & 0xffff;
		out.println("u2 " + minor_version + " # minor_version");
		int major_version = in.getShort() & 0xffff;
		out.println("u2 " + major_version + " # major_version");

		out.println();
		out.println("# Begin constant pool section");
		int constant_pool_count = in.getShort() & 0xffff;
		out.println("u2 " + constant_pool_count + " # constant_pool_count");
		for (int i = 1; i < constant_pool_count; i ++)
			if (dump_cp_info(in, out, i))
				i++;
		out.println("# End constant pool section");
		out.println();

		for (String key : new String[]{"access_flags", "this_class", "super_class"})
			out.println("u2 " + (in.getShort() & 0xffff) + " # " + key);

		int interfaces_count = in.getShort() & 0xffff;
		out.println("u2 " + interfaces_count + " # interfaces_count");
		for (int i = 0; i < interfaces_count; i ++) {
			if (i % 20 == 0) out.print("u2 ");
			out.print(in.getShort() & 0xffff);
			if (i % 20 == 19 || i == interfaces_count - 1)
				out.println();
			else
				out.print(",");
		}

		out.println();
		out.println("# Begin fields section");
		int fields_count = in.getShort() & 0xffff;
		out.println("u2 " + fields_count + " # fields_count");
		for (int i = 0; i < fields_count; i ++)
			dump_fieldmethod_info(in, out, false);
		out.println("# End fields section");

		out.println();
		out.println("# Begin methods section");
		int methods_count = in.getShort() & 0xffff;
		out.println("u2 " + methods_count + " # methods_count");
		for (int i = 0; i < methods_count; i ++)
			dump_fieldmethod_info(in, out, true);
		out.println("# End methods section");
		out.println();

		out.println("# Begin attributes section");
		int attributes_count = in.getShort() & 0xffff;
		out.println("u2 " + attributes_count + " # attributes_count");
		for (int i = 0; i < attributes_count; i ++)
			dump_attribute_info(in, out, 0);
		out.println("# End attributes section");

		out.close();
		inc.close();
	}

	private static void build (String txtpath, String clspath) throws Exception
	{
		BufferedReader in = new BufferedReader(new FileReader(txtpath));
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(clspath)));
		while (true) {
			String line = in.readLine();
			if (line == null) break;
			line = line.replaceAll("^\\s+", ""); // ltrim
			if (!line.startsWith("ascii ")) {
				line = line.replaceAll("#.*", "");
				line = line.trim();
			}
			if (line.isEmpty()) continue;

			if (line.startsWith("u1 ") || line.startsWith("u2 ") || line.startsWith("u4 ")) {
				for (String num : line.substring(3).split("[, ]")) {
					if (num.isEmpty()) continue;
					switch (line.charAt(1)) {
						case '1': out.writeByte(Integer.parseInt(num)); break;
						case '2': out.writeShort(Integer.parseInt(num)); break;
						case '4': out.writeInt((int)Long.parseLong(num)); break;
						default: assert false;
					}
				}
			} else if (line.startsWith("ascii ")) {
				out.writeShort(line.length() - 6);
				out.writeBytes(line.substring(6));
			} else if (line.startsWith("b ")) {
				for (int i = 2; i + 1 < line.length(); i += 2)
					out.write((Character.digit(line.charAt(i), 16) << 4) + Character.digit(line.charAt(i+1), 16));
			} else {
				System.err.println("Unknown line " + line);
				return;
			}
		}
		out.close();
		in.close();
	}

	private static void usage ()
	{
		System.out.println("Usage:");
		System.out.println(" java [d|dump] input-classfile.class [output-dumpfile.txt]");
		System.out.println(" java [b|build] input-dumpfile.txt [output-classfile.class]");
	}

	public static void main (String [] args) throws Exception
	{
		if (args.length == 3 && (args[0].equals("dump") || args[0].equals("d"))) {
			dump(args[1], args[2]);
		} else if (args.length == 2 && (args[0].equals("dump") || args[0].equals("d"))) {
			dump(args[1], args[1].replaceAll("\\.class$", ".clsexp.txt"));
		} else if (args.length == 3 && (args[0].equals("build") || args[0].equals("b"))) {
			build(args[1], args[2]);
		} else if (args.length == 2 && (args[0].equals("build") || args[0].equals("b"))) {
			build(args[1], args[1].replaceAll("(.clsexp)?.txt$", ".class"));
		} else {
			usage();
		}
	}
}
