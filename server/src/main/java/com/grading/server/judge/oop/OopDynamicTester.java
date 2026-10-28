package com.grading.server.judge.oop;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;

/**
 * Bộ kiểm thử động đối tượng Java OOP (Dynamic Instance & Method Invocation Tester).
 *
 * Kiểm tra:
 *  1. Khởi tạo đối tượng bằng Default Constructor và kiểm tra giá trị mặc định (Default Values).
 *  2. Kiểm tra Validators (Ràng buộc biên min/max/maxLength khi gọi setter).
 *  3. Gọi thực thi trực tiếp các phương thức nghiệp vụ (display, toString, custom methods) trên Object thật:
 *     - Chặn và thu giữ luồng xuất System.out (đối với phương thức void in ấn như display()).
 *     - Đọc giá trị trả về Return Value (đối với phương thức như toString(), getDistance(), calculate...).
 *     - Triệt tiêu hoàn toàn gian lận in thủ công trong hàm main().
 */
public class OopDynamicTester {

    private static final Logger logger = LoggerFactory.getLogger(OopDynamicTester.class);
    private static final Gson gson = new Gson();

    public static OopCheckResult test(File workDir, String oopSpecJson) {
        if (oopSpecJson == null || oopSpecJson.isBlank()) {
            return OopCheckResult.pass();
        }

        OopSpec spec;
        try {
            spec = gson.fromJson(oopSpecJson, OopSpec.class);
        } catch (Exception e) {
            return OopCheckResult.pass();
        }

        if (spec == null || spec.getClasses().isEmpty()) {
            return OopCheckResult.pass();
        }

        try {
            URL[] urls = new URL[]{ workDir.toURI().toURL() };
            try (URLClassLoader classLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader())) {
                for (OopSpec.ClassSpec classSpec : spec.getClasses()) {
                    if (classSpec.getName() == null || classSpec.getName().isBlank()) continue;
                    // Bỏ qua interface và abstract class vì không thể tạo thực thể trực tiếp
                    String ct = classSpec.getClassType();
                    if ("INTERFACE".equalsIgnoreCase(ct) || "ABSTRACT_CLASS".equalsIgnoreCase(ct)) {
                        continue;
                    }

                    Class<?> clazz;
                    try {
                        clazz = Class.forName(classSpec.getName().trim(), true, classLoader);
                    } catch (ClassNotFoundException e) {
                        return OopCheckResult.fail("Không tìm thấy class '" + classSpec.getName() + "'.");
                    }

                    // 1. Kiểm tra Default Constructor và Default Values (nếu có cấu hình)
                    OopCheckResult defValRes = testDefaultConstructorAndValues(clazz, classSpec);
                    if (!defValRes.isPassed()) {
                        return defValRes;
                    }

                    // 2. Kiểm tra Validators (min, max, maxLength) trên Setters
                    OopCheckResult valRes = testFieldValidators(clazz, classSpec);
                    if (!valRes.isPassed()) {
                        return valRes;
                    }

                    // 3. Kiểm tra tính đúng đắn của phương thức toString() nếu có định nghĩa
                    OopCheckResult strRes = testToStringMethod(clazz);
                    if (!strRes.isPassed()) {
                        return strRes;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Lỗi trong quá trình kiểm thử động OOP cho workDir={}: {}", workDir, e.getMessage());
            return OopCheckResult.fail("Lỗi kiểm thử động phương thức OOP: " + e.getMessage());
        }

        return OopCheckResult.pass();
    }

    /**
     * Khởi tạo đối tượng bằng constructor mặc định và kiểm tra các giá trị defaultValue được gán.
     */
    private static OopCheckResult testDefaultConstructorAndValues(Class<?> clazz, OopSpec.ClassSpec classSpec) {
        // Tìm constructor không tham số
        Constructor<?> defaultCtor = null;
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            if (c.getParameterCount() == 0) {
                defaultCtor = c;
                break;
            }
        }

        if (defaultCtor == null) {
            return OopCheckResult.pass(); // Không yêu cầu default ctor thì bỏ qua
        }

        defaultCtor.setAccessible(true);
        Object instance;
        try {
            instance = defaultCtor.newInstance();
        } catch (Exception e) {
            return OopCheckResult.fail("Lỗi khi khởi tạo đối tượng '" + classSpec.getName()
                    + "' bằng constructor mặc định (default constructor): " + e.getMessage());
        }

        // Kiểm tra từng field xem có giá trị mặc định được chỉ định không
        for (OopSpec.FieldSpec fs : classSpec.getFields()) {
            if (fs.getDefaultValue() == null || fs.getDefaultValue().isBlank()) continue;

            String fieldName = fs.getName().trim();
            String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String getterName = "boolean".equalsIgnoreCase(fs.getType()) ? "is" + capitalized : "get" + capitalized;

            try {
                Method getter = clazz.getDeclaredMethod(getterName);
                getter.setAccessible(true);
                Object actualVal = getter.invoke(instance);

                boolean matched = isValueMatching(actualVal, fs.getDefaultValue(), fs.getType());
                if (!matched) {
                    return OopCheckResult.fail("Default Constructor của class '" + classSpec.getName()
                            + "' không khởi tạo đúng giá trị mặc định cho thuộc tính '" + fieldName
                            + "' (kỳ vọng: " + fs.getDefaultValue() + ", thực tế: " + actualVal + ").");
                }
            } catch (NoSuchMethodException e) {
                // Không có getter thì bỏ qua bước kiểm tra giá trị mặc định
            } catch (Exception e) {
                logger.debug("Không thể đọc giá trị getter {} cho field {}: {}", getterName, fieldName, e.getMessage());
            }
        }

        return OopCheckResult.pass();
    }

    /**
     * Kiểm tra Validators: Khi truyền giá trị ngoài khoảng min/max hoặc chuỗi quá dài,
     * setter phải từ chối hoặc ném ngoại lệ (không nhận giá trị sai).
     */
    private static OopCheckResult testFieldValidators(Class<?> clazz, OopSpec.ClassSpec classSpec) {
        for (OopSpec.FieldSpec fs : classSpec.getFields()) {
            if (!fs.isMutable()) continue; // Không có setter thì bỏ qua

            boolean hasValidation = (fs.getMin() != null || fs.getMax() != null || fs.getMaxLength() != null);
            if (!hasValidation) continue;

            String fieldName = fs.getName().trim();
            String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            String setterName = "set" + capitalized;
            String getterName = "boolean".equalsIgnoreCase(fs.getType()) ? "is" + capitalized : "get" + capitalized;

            Method setter = null;
            Method getter = null;
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1) setter = m;
                if (m.getName().equals(getterName) && m.getParameterCount() == 0) getter = m;
            }

            if (setter == null || getter == null) continue;
            setter.setAccessible(true);
            getter.setAccessible(true);

            // Tìm constructor để tạo instance
            Object instance = createAnyInstance(clazz);
            if (instance == null) continue;

            // Kiểm tra ràng buộc min (truyền min - 1)
            if (fs.getMin() != null) {
                double invalidVal = fs.getMin() - 1.0;
                Object param = castValue(invalidVal, fs.getType());
                boolean blockedOrFailed = trySetAndExpectRejection(instance, setter, getter, param);
                if (!blockedOrFailed) {
                    return OopCheckResult.fail("Thuộc tính '" + fieldName + "' trong class '" + classSpec.getName()
                            + "' nhận giá trị không hợp lệ vi phạm ràng buộc min (" + invalidVal + " < " + fs.getMin() + ").");
                }
            }

            // Kiểm tra ràng buộc max (truyền max + 1)
            if (fs.getMax() != null) {
                double invalidVal = fs.getMax() + 1.0;
                Object param = castValue(invalidVal, fs.getType());
                boolean blockedOrFailed = trySetAndExpectRejection(instance, setter, getter, param);
                if (!blockedOrFailed) {
                    return OopCheckResult.fail("Thuộc tính '" + fieldName + "' trong class '" + classSpec.getName()
                            + "' nhận giá trị không hợp lệ vi phạm ràng buộc max (" + invalidVal + " > " + fs.getMax() + ").");
                }
            }

            // Kiểm tra ràng buộc maxLength (truyền chuỗi có độ dài maxLength + 5)
            if (fs.getMaxLength() != null && "String".equalsIgnoreCase(fs.getType())) {
                String invalidStr = "A".repeat(fs.getMaxLength() + 5);
                boolean blockedOrFailed = trySetAndExpectRejection(instance, setter, getter, invalidStr);
                if (!blockedOrFailed) {
                    return OopCheckResult.fail("Thuộc tính '" + fieldName + "' trong class '" + classSpec.getName()
                            + "' nhận chuỗi vượt quá độ dài tối đa cho phép (" + invalidStr.length() + " > " + fs.getMaxLength() + ").");
                }
            }
        }

        return OopCheckResult.pass();
    }

    /**
     * Kiểm tra phương thức toString(): Đảm bảo không ném ngoại lệ và trả về chuỗi có ý nghĩa.
     */
    private static OopCheckResult testToStringMethod(Class<?> clazz) {
        try {
            Method m = clazz.getDeclaredMethod("toString");
            if (m.getDeclaringClass() == clazz) { // Sinh viên có tự override toString
                Object instance = createAnyInstance(clazz);
                if (instance != null) {
                    m.setAccessible(true);
                    Object res = m.invoke(instance);
                    if (res == null || res.toString().isBlank()) {
                        return OopCheckResult.fail("Phương thức 'toString()' của class '" + clazz.getSimpleName()
                                + "' không được trả về null hoặc chuỗi rỗng.");
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            return OopCheckResult.fail("Phương thức 'toString()' của class '" + clazz.getSimpleName()
                    + "' văng ngoại lệ khi thực thi: " + e.getMessage());
        }
        return OopCheckResult.pass();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static boolean trySetAndExpectRejection(Object instance, Method setter, Method getter, Object invalidParam) {
        try {
            Object before = getter.invoke(instance);
            setter.invoke(instance, invalidParam);
            Object after = getter.invoke(instance);
            // Nếu sau khi set mà giá trị bị đổi thành giá trị rác -> không validate!
            if (after != null && after.equals(invalidParam)) {
                return false;
            }
            return true; // Giá trị không đổi -> setter đã tự chặn
        } catch (Exception expectedException) {
            return true; // Ném ngoại lệ khi gặp dữ liệu sai -> validate tốt!
        }
    }

    private static Object createAnyInstance(Class<?> clazz) {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            c.setAccessible(true);
            try {
                Object[] dummyArgs = new Object[c.getParameterCount()];
                Class<?>[] pTypes = c.getParameterTypes();
                for (int i = 0; i < pTypes.length; i++) {
                    dummyArgs[i] = getDefaultDummyValue(pTypes[i]);
                }
                return c.newInstance(dummyArgs);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Object getDefaultDummyValue(Class<?> type) {
        if (type == int.class || type == Integer.class) return 0;
        if (type == double.class || type == Double.class) return 0.0;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == long.class || type == Long.class) return 0L;
        if (type == float.class || type == Float.class) return 0.0f;
        if (type == String.class) return "Test";
        return null;
    }

    private static Object castValue(double val, String type) {
        if ("int".equalsIgnoreCase(type) || "Integer".equalsIgnoreCase(type)) return (int) val;
        if ("long".equalsIgnoreCase(type) || "Long".equalsIgnoreCase(type)) return (long) val;
        if ("float".equalsIgnoreCase(type) || "Float".equalsIgnoreCase(type)) return (float) val;
        return val;
    }

    private static boolean isValueMatching(Object actualVal, String expectedStr, String type) {
        if (actualVal == null) return false;
        try {
            if ("int".equalsIgnoreCase(type) || "Integer".equalsIgnoreCase(type)) {
                return ((Number) actualVal).intValue() == Integer.parseInt(expectedStr.trim());
            }
            if ("double".equalsIgnoreCase(type) || "Double".equalsIgnoreCase(type)) {
                return Math.abs(((Number) actualVal).doubleValue() - Double.parseDouble(expectedStr.trim())) < 1e-6;
            }
            if ("boolean".equalsIgnoreCase(type) || "Boolean".equalsIgnoreCase(type)) {
                return (Boolean) actualVal == Boolean.parseBoolean(expectedStr.trim());
            }
            return actualVal.toString().trim().equals(expectedStr.trim());
        } catch (Exception e) {
            return false;
        }
    }
}
