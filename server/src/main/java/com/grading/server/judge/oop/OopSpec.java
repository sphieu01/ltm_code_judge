package com.grading.server.judge.oop;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu trúc đặc tả kiểm tra cấu trúc Lập trình Hướng đối tượng (Java OOP).
 * Được parse từ JSON trong cột problems.oop_spec.
 */
public class OopSpec {

    private List<ClassSpec> classes = new ArrayList<>();

    public List<ClassSpec> getClasses() {
        return classes != null ? classes : new ArrayList<>();
    }

    public void setClasses(List<ClassSpec> classes) {
        this.classes = classes;
    }

    public static class ClassSpec {
        private String name;
        private String classType; // "CLASS", "ABSTRACT_CLASS", "INTERFACE"
        private String superClass;
        private List<String> interfaces;
        private List<FieldSpec> fields = new ArrayList<>();
        private List<ConstructorSpec> constructors = new ArrayList<>();
        private List<MethodSpec> methods = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getClassType() { return classType != null ? classType : "CLASS"; }
        public void setClassType(String classType) { this.classType = classType; }

        public String getSuperClass() { return superClass; }
        public void setSuperClass(String superClass) { this.superClass = superClass; }

        public List<String> getInterfaces() { return interfaces != null ? interfaces : new ArrayList<>(); }
        public void setInterfaces(List<String> interfaces) { this.interfaces = interfaces; }

        public List<FieldSpec> getFields() { return fields != null ? fields : new ArrayList<>(); }
        public void setFields(List<FieldSpec> fields) { this.fields = fields; }

        public List<ConstructorSpec> getConstructors() { return constructors != null ? constructors : new ArrayList<>(); }
        public void setConstructors(List<ConstructorSpec> constructors) { this.constructors = constructors; }

        public List<MethodSpec> getMethods() { return methods != null ? methods : new ArrayList<>(); }
        public void setMethods(List<MethodSpec> methods) { this.methods = methods; }
    }

    public static class FieldSpec {
        private String name;
        private String type; // e.g. "String", "int", "double"
        private String modifier; // "private", "public", "protected", "default"

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getModifier() { return modifier != null ? modifier : "private"; }
        public void setModifier(String modifier) { this.modifier = modifier; }
    }

    public static class ConstructorSpec {
        private String modifier; // "public", "protected", "private"
        private List<String> parameterTypes = new ArrayList<>();

        public String getModifier() { return modifier != null ? modifier : "public"; }
        public void setModifier(String modifier) { this.modifier = modifier; }

        public List<String> getParameterTypes() { return parameterTypes != null ? parameterTypes : new ArrayList<>(); }
        public void setParameterTypes(List<String> parameterTypes) { this.parameterTypes = parameterTypes; }
    }

    public static class MethodSpec {
        private String name;
        private String returnType; // "void", "String", "int", etc.
        private String modifier; // "public", "protected", "private", "abstract"
        private List<String> parameterTypes = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getReturnType() { return returnType != null ? returnType : "void"; }
        public void setReturnType(String returnType) { this.returnType = returnType; }

        public String getModifier() { return modifier != null ? modifier : "public"; }
        public void setModifier(String modifier) { this.modifier = modifier; }

        public List<String> getParameterTypes() { return parameterTypes != null ? parameterTypes : new ArrayList<>(); }
        public void setParameterTypes(List<String> parameterTypes) { this.parameterTypes = parameterTypes; }
    }
}
