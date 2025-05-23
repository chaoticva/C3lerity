// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FocusModeModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FocusModePassFactory implements TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  private static final Key<Set<RangeMarker>> FOCUS_REGIONS_FROM_PASS = Key.create("editor.focus.mode.segmentsFromPass");
  private static final LanguageExtension<FocusModeProvider> EP_NAME = new LanguageExtension<>("com.intellij.focusModeProvider");
  private static final long MAX_ALLOWED_TIME = 100;
  private static final Logger LOG = Logger.getInstance(FocusModePassFactory.class);

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1);
  }

  @Override
  public @Nullable TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    return isEnabled() && (EditorUtil.isRealFileEditor(editor) || ApplicationManager.getApplication().isUnitTestMode()) && editor instanceof EditorImpl
           ? new FocusModePass(editor, psiFile)
           : null;
  }

  static boolean isEnabled() {
    return EditorSettingsExternalizable.getInstance().isFocusMode();
  }

  static @Nullable List<? extends Segment> calcFocusZones(@Nullable PsiFile file) {
    if (file == null || !isEnabled()) {
      return null;
    }

    return CachedValuesManager.getCachedValue(file, () -> {
      FileViewProvider provider = file.getViewProvider();

      List<Segment> segments = null;
      for (FocusModeProvider p : EP_NAME.allForLanguageOrAny(provider.getBaseLanguage())) {
        segments = ContainerUtil.append(calcFocusZones(p, file), file.getTextRange());
        break;
      }
      return CachedValueProvider.Result.create(segments, file);
    });
  }

  /**
   * Computes focus zones for the {@code psiFile} using {@code focusModeProvider}. Additionally, warns if zones building took too long
   * (longer than {@link #MAX_ALLOWED_TIME} ms.
   */
  private static @NotNull List<? extends Segment> calcFocusZones(@NotNull FocusModeProvider focusModeProvider, @NotNull PsiFile psiFile) {
    Ref<List<? extends Segment>> resultRef = Ref.create();
    long executionTime = TimeoutUtil.measureExecutionTime(() -> resultRef.set(focusModeProvider.calcFocusZones(psiFile)));
    if (executionTime > MAX_ALLOWED_TIME) {
      LOG.warn("Focus zones collecting took too long: " + executionTime + "ms; " +
               "Provider: " + focusModeProvider.getClass().getSimpleName() + "; " +
               "File size: " + psiFile.getTextLength() + "; " +
               "Ranges collected: " + resultRef.get().size());
    }
    return resultRef.get();
  }

  static void setToEditor(@NotNull List<? extends Segment> zones, Editor editor) {
    mergeSegments(editor, zones);
  }

  private static void mergeSegments(Editor editor, @NotNull List<? extends Segment> zones) {
    if (!(editor instanceof EditorImpl)) return;
    FocusModeModel focusModeModel = ((EditorImpl)editor).getFocusModeModel();

    Set<RangeMarker> focusRegions = ((EditorImpl)editor).putUserDataIfAbsent(FOCUS_REGIONS_FROM_PASS, new HashSet<>());
    Set<RangeMarker> invalidFocusRegions = new HashSet<>(focusRegions);
    for (Segment zone : zones) {
      RangeMarker foundRegion = focusModeModel.findFocusRegion(zone.getStartOffset(), zone.getEndOffset());
      if (foundRegion == null) {
        RangeMarker newRegion = focusModeModel.createFocusRegion(zone.getStartOffset(), zone.getEndOffset());
        focusRegions.add(newRegion);
      }
      else {
        if (focusRegions.contains(foundRegion)) {
          // The region exists and belongs to this provider. Leave in focusRegions.
          invalidFocusRegions.remove(foundRegion);
        }
        else {
          LOG.warn("Trying to add existing focus region. startOffset: " + zone.getStartOffset() + ", endOffset: " + zone.getEndOffset());
        }
      }
    }
    for (RangeMarker invalidFocusRegion : invalidFocusRegions) {
      // Dispose and delete invalid and removed focus markers
      focusModeModel.removeFocusRegion(invalidFocusRegion);
      focusRegions.remove(invalidFocusRegion);
    }
  }

  private static final class FocusModePass extends EditorBoundHighlightingPass {
    private List<? extends Segment> myZones;

    private FocusModePass(Editor editor, PsiFile file) {
      super(editor, file, false);
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
      myZones = calcFocusZones(myFile);
    }

    @Override
    public void doApplyInformationToEditor() {
      if (myZones != null) {
        setToEditor(myZones, myEditor);
        ((EditorImpl)myEditor).applyFocusMode();
      }
    }
  }
}
