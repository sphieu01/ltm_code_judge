package com.grading.server.judge.oop;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * Grader sử dụng Java Reflection API để kiểm tra cấu trúc mã nguồn đối với bài tập Java OOP.
 * Kiểm tra:
 *  - Tên class, abstract class, interface
 *  - Quan hệ kế thừa (extends) và cài đặt giao diện (implements)
 *  - Danh sách thuộc tính (fields), kiểu dữ liệu và access modifier (bắt buộc private nếu đóng gói)
 *  - Constructors và tham số truyền vào
 *  - Phương thức (methods), kiểu trả về, tham số và access modifier
 *
 * Nếu vi phạm bất kỳ tiêu chí nào, trả về OopCheckResult.fail kèm lý do chi tiết để gán CE ngay lập tức.
 */
public class OopReflectionGrader {

    private static final Logger logger = LoggerFactory.getLogger(OopReflectionGrader.class);
    private static final Gson gson = new Gson();

    public static OopCheckResult check(File workDir, String oopSpecJson) {
        if (oopSpecJson == null || oopSpecJson.isBlank()) {
            return OopCheckResult.pass();
        }

        OopSpec spec;
        try {
            spec = gson.fromJson(oopSpecJson, OopSpec.class);
        } catch (Exception e) {
            logger.warn("Không thể parse oop_spec JSON: {}", e.getMessage());
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

                    // 1. Kiểm tra tồn tại Class
                    Class<?> clazz;
                    try {
                        clazz = Class.forName(classSpec.getName().trim(), true, classLoader);
                    } catch (ClassNotFoundException e) {
                        return OopCheckResult.fail("Không tìm thấy class '" + classSpec.getName() + "'. Hãy đảm bảo khai báo đúng tên lớp.");
                    }

                    // 2. Kiểm tra loại Class (Interface / Abstract Class / Concrete Class)
                    String ct = classSpec.getClassType();
                    if ("INTERFACE".equalsIgnoreCase(ct)) {
                        if (!clazz.isInterface()) {
                            return OopCheckResult.fail("'" + classSpec.getName() + "' phải là một interface.");
                        }
                    } else if ("ABSTRACT_CLASS".equalsIgnoreCase(ct)) {
                        if (!Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface()) {
                            return OopCheckResult.fail("Class '" + classSpec.getName() + "' phải là một abstract class.");
                        }
                    } else {
                        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                            return OopCheckResult.fail("Class '" + classSpec.getName() + "' phải là một class cụ thể thông thường (concrete class).");
                        }
                    }

                    // 3. Kiểm tra kế thừa (superClass)
                    if (classSpec.getSuperClass() != null && !classSpec.getSuperClass().isBlank()) {
                        Class<?> superClass = clazz.getSuperclass();
                        if (superClass == null || !superClass.getSimpleName().equalsIgnoreCase(classSpec.getSuperClass().trim())) {
                            return OopCheckResult.fail("Class '" + classSpec.getName() + "' phải kế thừa (extends) từ class '" + classSpec.getSuperClass() + "'.");
                        }
                    }

                    // 4. Kiểm tra cài đặt Interface
                    if (classSpec.getInterfaces() != null && !classSpec.getInterfaces().isEmpty()) {
                        Class<?>[] interfaces = clazz.getInterfaces();
                        for (String expectedItf : classSpec.getInterfaces()) {
                            if (expectedItf == null || expectedItf.isBlank()) continue;
                            boolean matched = false;
                            for (Class<?> itf : interfaces) {
                                if (itf.getSimpleName().equalsIgnoreCase(expectedItf.trim())) {
                                    matched = true;
                                    break;
                                }
                            }
                            if (!matched) {
                                return OopCheckResult.fail("Class '" + classSpec.getName() + "' phải cài đặt (implements) interface '" + expectedItf + "'.");
                            }
                        }
                    }

                    // 5. Kiểm tra Thuộc tính (Fields & Access Modifiers)
                    if (classSpec.getFields() != null) {
                        Field[] declaredFields = clazz.getDeclaredFields();
                        for (OopSpec.FieldSpec fieldSpec : classSpec.getFields()) {
                            if (fieldSpec.getName() == null || fieldSpec.getName().isBlank()) continue;
                            Field matchedField = null;
                            for (Field f : declaredFields) {
                                if (f.getName().equals(fieldSpec.getName().trim())) {
                                    matchedField = f;
                                    break;
                                }
                            }
                            if (matchedField == null) {
                                return OopCheckResult.fail("Class '" + classSpec.getName() + "' thiếu thuộc tính (field) '" + fieldSpec.getName() + "'.");
                            }

                            // Access modifier (đóng gói dữ liệu)
                            String expectedMod = fieldSpec.getModifier();
                            if (expectedMod != null && !expectedMod.isBlank()) {
                                boolean modOk = checkModifier(matchedField.getModifiers(), expectedMod);
                                if (!modOk) {
                                    return OopCheckResult.fail("Thuộc tính '" + fieldSpec.getName() + "' trong class '" + classSpec.getName()
                                            + "' phải có access modifier là '" + expectedMod + "' (vi phạm tính đóng gói Encapsulation).");
                                }
                            }

                            // Kiểu dữ liệu thuộc tính
                            if (fieldSpec.getType() != null && !fieldSpec.getType().isBlank()) {
                                String actualType = matchedField.getType().getSimpleName();
                                if (!isTypeCompatible(actualType, fieldSpec.getType())) {
                                    return OopCheckResult.fail("Thuộc tính '" + fieldSpec.getName() + "' trong class '" + classSpec.getName()
                                            + "' phải có kiểu dữ liệu là '" + fieldSpec.getType() + "' (hiện tại là '" + actualType + "').");
                                }
                            }
                        }
                    }

                    // 6. Kiểm tra Constructors
                    if (classSpec.getConstructors() != null && !classSpec.getConstructors().isEmpty()) {
                        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
                        for (OopSpec.ConstructorSpec ctorSpec : classSpec.getConstructors()) {
                            List<String> expectedParams = ctorSpec.getParameterTypes();
                            boolean ctorFound = false;
                            for (Constructor<?> ctor : constructors) {
                                Class<?>[] paramTypes = ctor.getParameterTypes();
                                if (paramTypes.length == expectedParams.size()) {
                                    boolean allParamsMatch = true;
                                    for (int i = 0; i < paramTypes.length; i++) {
                                        if (!isTypeCompatible(paramTypes[i].getSimpleName(), expectedParams.get(i))) {
                                            allParamsMatch = false;
                                            break;
                                        }
                                    }
                                    if (allParamsMatch) {
                                        if (ctorSpec.getModifier() != null && !ctorSpec.getModifier().isBlank()) {
                                            boolean modOk = checkModifier(ctor.getModifiers(), ctorSpec.getModifier());
                                            if (!modOk) {
                                                return OopCheckResult.fail("Constructor (" + String.join(", ", expectedParams) + ") của class '"
                                                        + classSpec.getName() + "' phải có access modifier là '" + ctorSpec.getModifier() + "'.");
                                            }
                                        }
                                        ctorFound = true;
                                        break;
                                    }
                                }
                            }
                            if (!ctorFound) {
                                return OopCheckResult.fail("Class '" + classSpec.getName() + "' thiếu constructor với tham số: ("
                                        + String.join(", ", expectedParams) + ").");
                            }
                        }
                    }

                    // 7. Kiểm tra Methods
                    if (classSpec.getMethods() != null) {
                        Method[] declaredMethods = clazz.getDeclaredMethods();
                        for (OopSpec.MethodSpec methodSpec : classSpec.getMethods()) {
                            if (methodSpec.getName() == null || methodSpec.getName().isBlank()) continue;
                            List<String> expectedParams = methodSpec.getParameterTypes();
                            Method matchedMethod = null;
                            for (Method m : declaredMethods) {
                                if (m.getName().equals(methodSpec.getName().trim())) {
                                    Class<?>[] pTypes = m.getParameterTypes();
                                    if (pTypes.length == expectedParams.size()) {
                                        boolean paramsMatch = true;
                                        for (int i = 0; i < pTypes.length; i++) {
                                            if (!isTypeCompatible(pTypes[i].getSimpleName(), expectedParams.get(i))) {
                                                paramsMatch = false;
                                                break;
                                            }
                                        }
                                        if (paramsMatch) {
                                            matchedMethod = m;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (matchedMethod == null) {
                                return OopCheckResult.fail("Class '" + classSpec.getName() + "' thiếu phương thức '"
                                        + methodSpec.getName() + "(" + String.join(", ", expectedParams) + ")'.");
                            }

                            // Kiểu trả về
                            if (methodSpec.getReturnType() != null && !methodSpec.getReturnType().isBlank()) {
                                String actualReturn = matchedMethod.getReturnType().getSimpleName();
                                if (!isTypeCompatible(actualReturn, methodSpec.getReturnType())) {
                                    return OopCheckResult.fail("Phương thức '" + methodSpec.getName() + "' trong class '" + classSpec.getName()
                                            + "' phải có kiểu trả về là '" + methodSpec.getReturnType() + "' (hiện tại là '" + actualReturn + "').");
                                }
                            }

                            // Modifier phương thức
                            if (methodSpec.getModifier() != null && !methodSpec.getModifier().isBlank()) {
                                boolean modOk = checkModifier(matchedMethod.getModifiers(), methodSpec.getModifier());
                                if (!modOk) {
                                    return OopCheckResult.fail("Phương thức '" + methodSpec.getName() + "' trong class '" + classSpec.getName()
                                            + "' phải có access modifier là '" + methodSpec.getModifier() + "'.");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Lỗi khi kiểm tra Reflection OOP cho workDir={}: {}", workDir, e.getMessage(), e);
            return OopCheckResult.fail("Lỗi nội bộ khi soi cấu trúc OOP: " + e.getMessage());
        }

        return OopCheckResult.pass();
    }

    private static boolean checkModifier(int modifiers, String expectedMod) {
        if (expectedMod == null) return true;
        return switch (expectedMod.trim().toLowerCase()) {
            case "private" -> Modifier.isPrivate(modifiers);
            case "public" -> Modifier.isPublic(modifiers);
            case "protected" -> Modifier.isProtected(modifiers);
            case "abstract" -> Modifier.isAbstract(modifiers);
            default -> !Modifier.isPrivate(modifiers) && !Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers);
        };
    }

    private static boolean isTypeCompatible(String actual, String expected) {
        if (actual == null || expected == null) return false;
        actual = actual.trim();
        expected = expected.trim();
        if (actual.equalsIgnoreCase(expected)) return true;

        // Xử lý boxing / unboxing type alias thông dụng
        if (("int".equalsIgnoreCase(actual) && "Integer".equalsIgnoreCase(expected)) ||
            ("Integer".equalsIgnoreCase(actual) && "int".equalsIgnoreCase(expected))) return true;

        if (("double".equalsIgnoreCase(actual) && "Double".equalsIgnoreCase(expected)) ||
            ("Double".equalsIgnoreCase(actual) && "double".equalsIgnoreCase(expected))) return true;

        if (("boolean".equalsIgnoreCase(actual) && "Boolean".equalsIgnoreCase(expected)) ||
            ("Boolean".equalsIgnoreCase(actual) && "boolean".equalsIgnoreCase(expected))) return true;

        if (("char".equalsIgnoreCase(actual) && "Character".equalsIgnoreCase(expected)) ||
            ("Character".equalsIgnoreCase(actual) && "char".equalsIgnoreCase(expected))) return true;

        if (("long".equalsIgnoreCase(actual) && "Long".equalsIgnoreCase(expected)) ||
            ("Long".equalsIgnoreCase(actual) && "long".equalsIgnoreCase(expected))) return true;

        if (("float".equalsIgnoreCase(actual) && "Float".equalsIgnoreCase(expected)) ||
            ("Float".equalsIgnoreCase(actual) && "float".equalsIgnoreCase(expected))) return true;

        return false;
    }
}
