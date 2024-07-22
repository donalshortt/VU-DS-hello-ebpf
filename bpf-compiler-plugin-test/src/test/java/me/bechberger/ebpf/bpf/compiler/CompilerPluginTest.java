package me.bechberger.ebpf.bpf.compiler;

import me.bechberger.ebpf.annotations.EnumMember;
import me.bechberger.ebpf.annotations.bpf.NotUsableInJava;
import me.bechberger.ebpf.annotations.Type;
import me.bechberger.ebpf.annotations.bpf.MethodIsBPFRelatedFunction;
import me.bechberger.ebpf.annotations.Size;
import me.bechberger.ebpf.annotations.Unsigned;
import me.bechberger.ebpf.annotations.bpf.*;
import me.bechberger.ebpf.bpf.BPFJ;
import me.bechberger.ebpf.bpf.BPFProgram;
import me.bechberger.ebpf.bpf.GlobalVariable;
import me.bechberger.ebpf.runtime.helpers.BPFHelpers;
import me.bechberger.ebpf.shared.util.DiffUtil;
import me.bechberger.ebpf.type.BPFType.BPFIntType.Int128;
import me.bechberger.ebpf.type.BPFType.BPFIntType.UnsignedInt128;
import me.bechberger.ebpf.type.Enum;
import me.bechberger.ebpf.type.Ptr;
import me.bechberger.ebpf.type.Struct;
import me.bechberger.ebpf.type.Union;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompilerPluginTest {

    /**
     * Program with just a function call for testing that the compiler plugin is called
     */
    @BPF
    public static abstract class SimpleProgram extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                                
                int func(int x, int y);
                int func2(int x);
                """;

        @BuiltinBPFFunction
        @NotUsableInJava
        public int func(int x, int y) {
            throw new MethodIsBPFRelatedFunction();
        }

        @BuiltinBPFFunction("$arg1")
        @NotUsableInJava
        public int func2(int x, int y) {
            throw new MethodIsBPFRelatedFunction();
        }

        @BPFFunction
        public int simpleReturn(int x) {
            return 1;
        }

        @BPFFunction
        public int math(int x) {
            return func(x, x + 1) + 1;
        }

        @BPFFunction
        public int math2(int x) {
            return func2(x, x + 1) + 2;
        }

        @BPFFunction
        public void empty() {

        }
    }

    @Test
    public void testSimpleProgram() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                                
                int func(int x, int y);
                int func2(int x);
                                
                s32 simpleReturn(s32 x) {
                  return 1;
                }
                                
                s32 math(s32 x) {
                  return func(x, x + 1) + 1;
                }
                                
                s32 math2(s32 x) {
                  return x + 2;
                }
                                
                void empty() {
                                
                }
                """, BPFProgram.getCode(SimpleProgram.class));
    }

    @BPF
    public static abstract class TestPtr extends BPFProgram {
        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                """;

        @BPFFunction
        public int refAndDeref() {
            int value = 3;
            Ptr<Integer> ptr = Ptr.of(value);
            return ptr == Ptr.ofNull() ? 1 : 0;
        }

        @BPFFunction
        public int cast(Ptr<Integer> intPtr) {
            Ptr<Short> ptr = intPtr.cast();
            return ptr.val();
        }

        @BPFFunction
        public Ptr<Integer> increment(Ptr<Integer> ptr) {
            return ptr.add(1);
        }
    }

    @Test
    public void testPtr() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                                
                s32 refAndDeref() {
                  s32 value = 3;
                  s32 *ptr = &((value));
                  return ptr == ((void*)0) ? 1 : 0;
                }
                                
                s32 cast(s32 *intPtr) {
                  s16 *ptr = ((s16*)intPtr);
                  return ((s16)*(ptr));
                }
                                
                s32* increment(s32 *ptr) {
                  return (ptr + 1);
                }
                """, BPFProgram.getCode(TestPtr.class));
    }

    @BPF
    public static abstract class TestPrint extends BPFProgram {
        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @BPFFunction
        public void testPrint() {
            BPFHelpers.bpf_trace_printk("Hello, World!\\n", "Hello, World!\\n".length());
        }

        @BPFFunction
        public void testJavaPrint() {
            BPFJ.bpf_trace_printk("Hello, World!\\n");
        }

        @BPFFunction
        public void testJavaPrint2() {
            BPFJ.bpf_trace_printk("Hello, %s!\\n", "World");
        }
    }

    @Test
    public void testPrint() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                void testPrint() {
                  bpf_trace_printk((const char*)"Hello, World!\\\\n", 15);
                }
                                
                void testJavaPrint() {
                  bpf_trace_printk("Hello, World!\\\\n", 15);
                }
                                
                void testJavaPrint2() {
                  bpf_trace_printk("Hello, %s!\\\\n", 12, "World");
                }
                """, BPFProgram.getCode(TestPrint.class));
    }

    @BPF
    public static abstract class TestGlobalVariable extends BPFProgram {
        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        public final GlobalVariable<Integer> count = new GlobalVariable<>(42);

        @BPFFunction
        public void testGlobalVariable() {
            count.set(43);
            int currentCount = count.get();
            BPFJ.bpf_trace_printk("Count: %d\\n", currentCount);
        }
    }

    @Test
    public void testGlobalVariable() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                s32 count SEC(".data");
                                
                void testGlobalVariable() {
                  count = (43);
                  s32 currentCount = ((s32)count);
                  bpf_trace_printk("Count: %d\\\\n", 11, (currentCount));
                }
                """, BPFProgram.getCode(TestGlobalVariable.class));
    }

    void assertEqualsDiffed(String expected, String actual, boolean ignoreIncludes) {
        expected = ignoreIncludes ? removeIncludes(expected.strip()) : expected.strip();
        actual = ignoreIncludes ? removeIncludes(actual.strip()) : actual.strip();
        if (!expected.equals(actual)) {
            var diff = DiffUtil.diff(expected, actual);
            System.err.println("Diff: ");
            System.err.println(diff);
            assertEquals(expected, actual);
        }
    }

    void assertEqualsDiffed(String expected, String actual) {
        assertEqualsDiffed(expected, actual, true);
    }

    String removeIncludes(String code) {
        var lines = code.lines().filter(line -> !line.startsWith("#include ")).collect(Collectors.joining("\n"));
        if (lines.startsWith("\n")) {
            lines = lines.substring(1);
        }
        return lines;
    }

    @BPF
    public static abstract class TestString extends BPFProgram {
        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @BPFFunction
        public char stringAt(String str) {
            return str.charAt(0);
        }

        @BPFFunction
        public byte bytes(String str) {
            return str.getBytes()[0];
        }
    }

    @Test
    public void testString() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                char stringAt(char *str) {
                  return str[0];
                }
                                
                s8 bytes(char *str) {
                  return (str)[0];
                }
                """, BPFProgram.getCode(TestString.class));
    }

    @BPF
    public static abstract class TestArray extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @BPFFunction
        public int access(@Size(2) int[] arr) {
            return arr[0];
        }

        @BPFFunction
        public void create() {
            @Size(2) int[] arr = new int[2];
            arr[0] = 1;
            arr[1] = 2;
            BPFJ.bpf_trace_printk("Array: %d, %d\\n", arr[0], arr[1]);
        }

        @BPFFunction
        public void create2() {
            int[] arr = new int[2];
            int[] arr2 = {1, 2};
            int[] arr3 = new int[]{1, 2};
        }

        @BPFFunction
        public Ptr<Integer> toPtr(@Size(2) int[] arr) {
            return Ptr.of(arr);
        }
    }

    @Test
    public void testArray() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                s32 access(s32 arr[2]) {
                  return arr[0];
                }
                                
                void create() {
                  s32 arr[2];
                  arr[0] = 1;
                  arr[1] = 2;
                  bpf_trace_printk("Array: %d, %d\\\\n", 15, (arr[0]), (arr[1]));
                }
                                
                void create2() {
                  s32 arr[2];
                  s32 arr2[2] = {1, 2};
                  s32 arr3[2] = {1, 2};
                }
                                
                s32* toPtr(s32 arr[2]) {
                  return (arr);
                }
                """, BPFProgram.getCode(TestArray.class));
    }

    @BPF
    public static abstract class TestForLoopAndIf extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @BPFFunction
        public int forLoop() {
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += i;
            }
            return 1;
        }

        @BPFFunction
        public int ifStatement(int x) {
            if (x > 0) {
                return 1;
            }
            return 0;
        }

        @BPFFunction
        public int ifEleseStatement(int x) {
            if (x > 0) {
                return 1;
            } else {
                return 0;
            }
        }

        @BPFFunction
        public int ifElseIfElse(int x) {
            if (x > 0) {
                return 1;
            } else if (x < -10) {
                return -1;
            } else {
                return 0;
            }
        }
    }

    @Test
    public void testForLoopAndIf() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                s32 forLoop() {
                  s32 sum = 0;
                  for (s32 i = 0; i < 10; i++) {
                    sum += i;
                  }
                  return 1;
                }
                                
                s32 ifStatement(s32 x) {
                  if ((x > 0)) {
                    return 1;
                  }
                  return 0;
                }
                                
                s32 ifEleseStatement(s32 x) {
                  if ((x > 0)) {
                    return 1;
                  } else {
                    return 0;
                  }
                }
                                
                s32 ifElseIfElse(s32 x) {
                  if ((x > 0)) {
                    return 1;
                  } else if ((x < -10)) {
                    return -1;
                  } else {
                    return 0;
                  }
                }
                """, BPFProgram.getCode(TestForLoopAndIf.class));
    }

    @BPF
    public static abstract class TestComments extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        /**
         * Comment
         */
        @BPFFunction
        public int testComments() {
            // This is a comment
            return 1; // This is another comment
        }
    }

    @Test
    public void testComments() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                s32 testComments() {
                  return 1;
                }
                """, BPFProgram.getCode(TestComments.class));
    }

    @BPF
    public static abstract class TestFinalVariable extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @BPFFunction
        public int finalVariable() {
            final int i = 0;
            return i;
        }
    }

    @Test
    public void testFinalVariable() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                s32 finalVariable() {
                  s32 i = 0;
                  return i;
                }
                """, BPFProgram.getCode(TestFinalVariable.class));
    }

    @BPF
    public static abstract class EnumTest extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @Type
        enum TestEnum implements Enum<TestEnum> {
            A, B, @EnumMember(name = "D") C
        }

        @BPFFunction
        int ordinal(TestEnum e) {
            return (int) e.value();
        }

        @BPFFunction
        TestEnum ofValue(int ordinal) {
            return Enum.ofValue(ordinal);
        }

        @BPFFunction
        TestEnum access() {
            return TestEnum.A;
        }

        @BPFFunction
        TestEnum access2() {
            return TestEnum.C;
        }
    }

    @Test
    public void testEnum() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                enum TestEnum {
                  TEST_ENUM_A = 0,
                  TEST_ENUM_B = 1,
                  D = 2
                };
                                
                s32 ordinal(enum TestEnum e) {
                  return (s32)(long)(e);
                }
                                
                enum TestEnum ofValue(s32 ordinal) {
                  return (enum TestEnum)(enum TestEnum)(ordinal);
                }
                                
                enum TestEnum access() {
                  return TEST_ENUM_A;
                }
                                
                enum TestEnum access2() {
                  return D;
                }
                """, BPFProgram.getCode(EnumTest.class));
    }

    public static final int OUTER_CONSTANT = 100;

    @BPF
    public static abstract class TestConstants extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        static final int TEST_CONSTANT = 100;
        static final String TEST_CONSTANT_STRING = "Hello, World!";

        @BPFFunction
        public int constant() {
            return TEST_CONSTANT;
        }

        @BPFFunction
        public String constantString() {
            return TEST_CONSTANT_STRING;
        }

        @BPFFunction
        public int outerConstant() {
            return OUTER_CONSTANT;
        }
    }

    @Test
    public void testConstants() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                #define TEST_CONSTANT 100
                #define TEST_CONSTANT_STRING "Hello, World!"
                                
                s32 constant() {
                  return TEST_CONSTANT;
                }
                                
                char* constantString() {
                  return TEST_CONSTANT_STRING;
                }
                                
                s32 outerConstant() {
                  return 100;
                }
                """, BPFProgram.getCode(TestConstants.class));
    }

    @BPF
    public static abstract class TestStruct extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @Type
        static class Event extends Struct {
            @Unsigned
            int pid;
            @Size(256)
            String filename;
            @Size(16)
            String comm;
        }

        @BPFFunction
        int access(Event event) {
            return event.pid;
        }

        @BPFFunction
        void returnAndCreateEvent(Ptr<Event> evtPtr) {
            Event event = new Event();
            event.pid = 1;
            evtPtr.set(event);
        }
    }

    @Test
    public void testStruct() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                struct Event {
                  u32 pid;
                  char filename[256];
                  char comm[16];
                };
                               
                s32 access(struct Event event) {
                  return event.pid;
                }
                                
                void returnAndCreateEvent(struct Event *evtPtr) {
                  struct Event event = (struct Event){};
                  event.pid = 1;
                  *(evtPtr) = event;
                }
                """, BPFProgram.getCode(TestStruct.class));
    }

    @BPF
    public static abstract class TestNotUsableInJavaStruct extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @Type
        @NotUsableInJava
        static class Event extends Struct {
            @Unsigned
            int pid;
        }

        @BPFFunction
        int use(Event event) {
            Event event2 = new Event();
            event2.pid = event.pid;
            return event2.pid;
        }
    }

    @Test
    public void testNotUsableInJavaStruct() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                struct Event {
                  u32 pid;
                };
                                
                s32 use(struct Event event) {
                  struct Event event2 = (struct Event){};
                  event2.pid = event.pid;
                  return event2.pid;
                }
                """, BPFProgram.getCode(TestNotUsableInJavaStruct.class));
    }

    @BPF
    public static abstract class TestUnion extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @Type
        static class SampleUnion extends Union {
            @Unsigned
            int ipv4;
            long count;
        }

        @BPFFunction
        int access(SampleUnion address) {
            return address.ipv4;
        }

        @BPFFunction
        long createAddress() {
            SampleUnion address = new SampleUnion();
            address.ipv4 = 1;
            return address.count;
        }
    }

    @Test
    public void testUnion() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                union SampleUnion {
                  u32 ipv4;
                  s64 count;
                };
                                
                s32 access(union SampleUnion address) {
                  return address.ipv4;
                }
                                
                s64 createAddress() {
                  union SampleUnion address = (union SampleUnion){};
                  address.ipv4 = 1;
                  return address.count;
                }
                """, BPFProgram.getCode(TestUnion.class));
    }

    @BPF
    public static abstract class TestRecordStruct extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @Type
        record Event(@Unsigned int pid, @Size(256) String filename) {
        }

        @BPFFunction
        int access(Event event) {
            int i = event.pid();
            return event.pid;
        }

        @BPFFunction
        void createEvent() {
            Event event = new Event(1, "file");
            BPFJ.setField(event, "pid", 2);
        }
    }

    @Test
    public void testRecordStruct() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                struct Event {
                  u32 pid;
                  char filename[256];
                };
                                
                s32 access(struct Event event) {
                  s32 i = event.pid;
                  return event.pid;
                }
                                
                void createEvent() {
                  struct Event event = (struct Event){.pid = 1, .filename = "file"};
                  (event).pid = (2);
                }
                """, BPFProgram.getCode(TestRecordStruct.class));
    }

    @BPF
    public static abstract class TestInt128 extends BPFProgram {

        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @BPFFunction
        void create() {
            Int128 i = Int128.of(1, 2);
        }

        @BPFFunction
        long lower(Int128 i) {
            return i.lower();
        }

        @BPFFunction
        long upper(Int128 i) {
            return i.toUnsigned().upper();
        }

        @BPFFunction
        long lowerUnsigned(UnsignedInt128 i) {
            return i.lower();
        }
    }

    @Test
    public void testInt128() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                void create() {
                  __int128 i = (((__int128)1) << 64) | (2);
                }
                                
                s64 lower(__int128 i) {
                  return (s64)(i);
                }
                                
                s64 upper(__int128 i) {
                  return (s64)((i) >> 64);
                }
                                
                s64 lowerUnsigned(__int128 unsigned i) {
                  return (s64)(i);
                }
                """, BPFProgram.getCode(TestInt128.class));
    }

    @BPF
    public static abstract class TestBPFFunctionTemplates extends BPFProgram {

        @BPFFunction(
                callTemplate = "$name($arg1, $arg1)",
                headerTemplate = "$return $name($paramType1 $paramName1, $paramType1 y);",
                lastStatement = "(void*)0;",
                section = "section"
        )
        public void called(int x) {

        }

        @BPFFunction
        public void caller() {
            called(1);
        }
    }

    @Test
    public void testBPFFunctionTemplates() {
        assertEqualsDiffed("""
                SEC("section") void called(s32 x, s32 y) {
                  (void*)0;
                }
                                
                void caller() {
                  called(1, 1);
                }
                """, BPFProgram.getCode(TestBPFFunctionTemplates.class));
        assertEquals(List.of(), BPFProgram.getAutoAttachableBPFPrograms(TestBPFFunctionTemplates.class));
    }

    @BPFInterface
    interface TestInterface {
        @BPFFunction(
                callTemplate = "$name($arg1, $arg1)",
                headerTemplate = "int $name($paramType1 $paramName1, $paramType1 y)",
                lastStatement = "return 1;",
                section = "section",
                autoAttach = true
        )
        void func(String name);
    }

    @BPF
    static abstract class TestInterfaceImpl extends BPFProgram implements TestInterface {
        @Override
        public void func(String name) {
            BPFJ.bpf_trace_printk("Hello, %s!\\n", name);
        }
    }

    @Test
    public void testBPFFunctionTemplatesInterface() {
        assertEqualsDiffed("""
                SEC("section") int func(char* name, char* y) {
                  bpf_trace_printk("Hello, %s!\\\\n", 12, name);
                  return 1;
                }
                """, BPFProgram.getCode(TestInterfaceImpl.class));
        assertEquals(List.of("func"), BPFProgram.getAutoAttachableBPFPrograms(TestInterfaceImpl.class));
    }

    @BPF
    static abstract class TestStringBody extends BPFProgram {
        static final String EBPF_PROGRAM = """
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                """;

        @BPFFunction(
                lastStatement = "bpf_trace_printk(\"%s\", 2, code);"
        )
        public void body() {
            String body = """
                    char* code = "Hello, World!";
                    """;
            throw new MethodIsBPFRelatedFunction();
        }
    }

    @Test
    public void testStringBody() {
        assertEqualsDiffed("""
                #include "vmlinux.h"
                #include <bpf/bpf_helpers.h>
                                
                void body() {
                  char* code = "Hello, World!";
                  bpf_trace_printk("%s", 2, code);
                }
                """, BPFProgram.getCode(TestStringBody.class));
    }
}
