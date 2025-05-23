// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.util.IntellijInternalApi

class UElementAsPsiInspectionTest : PluginModuleTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("package org.jetbrains.uast; public interface UElement {}")
    myFixture.addClass("""package com.intellij.psi; public interface PsiElement {
      | PsiElement getParent();
      | PsiElement getSelf();
      |}""".trimMargin())
    myFixture.addClass("package com.intellij.psi; public interface PsiClass extends PsiElement {}")
    myFixture.addClass("""package org.jetbrains.uast; public interface UClass extends UElement, com.intellij.psi.PsiClass {
      | void uClassMethod();
      |
      | @Override
      | UClass getSelf();
      |}""".trimMargin())

    myFixture.enableInspections(UElementAsPsiInspection())
  }

  fun testPassAsPsiClass() {
    //language=JAVA
    myFixture.addClass("""
      import org.jetbrains.uast.UClass;
      import com.intellij.psi.PsiClass;

      class UastUsage {

          public void processUClassCaller(UClass uClass){
             processUClass(uClass);
          }

          public void processUClass(UClass uClass){ processPsiClass(<warning descr="Usage of UElement as PsiElement is not recommended">uClass</warning>); }

          public void processPsiClass(PsiClass psiClass){ }

      }
    """.trimIndent())
    myFixture.testHighlighting("UastUsage.java")

  }

  fun testAssignment() {
    //language=JAVA
    myFixture.addClass("""
      import org.jetbrains.uast.UClass;
      import com.intellij.psi.PsiClass;

      class UastUsage {

          PsiClass psiClass = null;

          public UastUsage(UClass uClass){
             psiClass = <warning descr="Usage of UElement as PsiElement is not recommended">uClass</warning>;
             PsiClass localPsiClass = <warning descr="Usage of UElement as PsiElement is not recommended">uClass</warning>;
             localPsiClass = null;
          }

      }
    """.trimIndent())
    myFixture.testHighlighting("UastUsage.java")

  }

  fun testCast() {
    //language=JAVA
    myFixture.addClass("""
      import org.jetbrains.uast.UClass;
      import com.intellij.psi.PsiClass;
      import org.jetbrains.uast.UElement;

      class UastUsage {

          public UastUsage(UElement uElement){
             if(<warning descr="Usage of UElement as PsiElement is not recommended">uElement</warning> instanceof PsiClass){
                PsiClass psiClass = (PsiClass)<warning descr="Usage of UElement as PsiElement is not recommended">uElement</warning>;
             }
             if(uElement instanceof UClass){
                UClass uClass = (UClass)uElement;
             }
          }

      }
    """.trimIndent())
    myFixture.testHighlighting("UastUsage.java")

  }

  fun testCallParentMethod() {
    //language=JAVA
    myFixture.addClass("""
      import org.jetbrains.uast.UClass;
      import com.intellij.psi.PsiClass;

      class UastUsage {

          public UastUsage(UClass uClass){
             <warning descr="Usage of UElement as PsiElement is not recommended">uClass.getParent()</warning>;
             uClass.uClassMethod();
          }

      }
    """.trimIndent())
    myFixture.testHighlighting("UastUsage.java")

  }

  fun testImplementAndCallParentMethod() {
    //language=JAVA
    myFixture.addClass("""
      import org.jetbrains.uast.UClass;
      import com.intellij.psi.*;

      public class UastUsage {

          public UastUsage(){
            UClassImpl impl = new UClassImpl();
            <warning descr="Usage of UElement as PsiElement is not recommended">impl.getParent()</warning>;
            impl.uClassMethod();
            impl.getSelf();
          }

           public UastUsage(UClass impl){
            <warning descr="Usage of UElement as PsiElement is not recommended">impl.getParent()</warning>;
            impl.uClassMethod();
            impl.getSelf();
          }

      }

      class UClassImpl implements UClass {

        @Override
        public PsiElement getParent(){ return null; }

        @Override
        public UClass getSelf(){ return this; }

        @Override
        public void uClassMethod(){  }
      }

    """.trimIndent())
    myFixture.testHighlighting("UastUsage.java")

  }

  fun testArrays() {
    //language=JAVA
    myFixture.addClass("""
      import org.jetbrains.uast.UClass;
      import com.intellij.psi.*;

      class UastUsage {

        UClass[] getClasses() { return null; }

        void usage() {
          PsiClass[] classes = <warning descr="Usage of UElement as PsiElement is not recommended">getClasses()</warning>;
        }

      }
    """.trimIndent())
    myFixture.testHighlighting("UastUsage.java")
  }

  fun testReturn() {
    //language=JAVA
    myFixture.addClass("""
      import org.jetbrains.uast.UClass;
      import com.intellij.psi.*;

      class UastUsage {

        PsiClass returnUClass(UClass uClass) {
          return <warning descr="Usage of UElement as PsiElement is not recommended">uClass</warning>;
        }

      }
    """.trimIndent())
    myFixture.testHighlighting("UastUsage.java")
  }

  // there was a bug reporting warning for nested methods multiple times (during analyzing top-level method and all nested)
  fun testNestedMethods() {
    myFixture.addClass("""
      import com.intellij.psi.PsiElement;
      import org.jetbrains.uast.UClass;
      import org.jetbrains.uast.UElement;

      class UastUsage {
        void method(UClass uClass) {
          new Runnable() {
            public void run() {
              new Runnable() {
                public void run() {
                  new Runnable() {
                    public void run() {
                      <warning descr="Usage of UElement as PsiElement is not recommended">uClass.getParent()</warning>;
                    }
                  };
                }
              };
            }
          };
        }
      }
      """.trimIndent())
    myFixture.testHighlighting("UastUsage.java")
  }
}