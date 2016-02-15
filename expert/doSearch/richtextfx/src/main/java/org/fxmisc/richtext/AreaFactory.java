package org.fxmisc.richtext;

import javafx.scene.text.TextFlow;

import java.util.function.BiConsumer;

/**
 * AreaFactory is a convenience class used to create StyledTextArea
 * and any of its subclasses. and optionally embed them
 * into a {@link VirtualizedScrollPane}.
 *
 */
public class AreaFactory {

    /* ********************************************************************** *
     *                                                                        *
     * StyledTextArea                                                         *
     *                                                                        *
     * ********************************************************************** */

    // StyledTextArea 1
    public static <PS, S> StyledTextArea<PS, S> styledTextArea(
            PS initialParagraphStyle, BiConsumer<TextFlow, PS> applyParagraphStyle,
            S initialTextStyle, BiConsumer<? super TextExt, S> applyStyle
    ) {
        return new StyledTextArea<>(
                initialParagraphStyle, applyParagraphStyle,
                initialTextStyle, applyStyle,
                true);
    }


    // StyledTextArea 2
    public static <PS, S> StyledTextArea<PS, S> styledTextArea(
            PS initialParagraphStyle, BiConsumer<TextFlow, PS> applyParagraphStyle,
            S initialTextStyle, BiConsumer<? super TextExt, S> applyStyle,
            boolean preserveStyle
    ) {
        return new StyledTextArea<>(
                initialParagraphStyle, applyParagraphStyle,
                initialTextStyle, applyStyle,
                preserveStyle);
    }

    // Clones StyledTextArea
    public static <PS, S> StyledTextArea<PS, S> cloneStyleTextArea(StyledTextArea<PS, S> area) {
        return new StyledTextArea<>(area.getInitialParagraphStyle(), area.getApplyParagraphStyle(),
                area.getInitialTextStyle(), area.getApplyStyle(),
                area.getModel().getContent(), area.isPreserveStyle());
    }

    /* ********************************************************************** *
     *                                                                        *
     * StyleClassedTextArea                                                   *
     *                                                                        *
     * ********************************************************************** */

    // StyleClassedTextArea 1
    public static StyleClassedTextArea styleClassedTextArea(boolean preserveStyle) {
        return new StyleClassedTextArea(preserveStyle);
    }

    // StyleClassedTextArea  2
    public static StyleClassedTextArea styleClassedTextArea() {
        return styleClassedTextArea(true);
    }

    // Clones StyleClassedTextArea
    public static StyleClassedTextArea cloneStyleClassedTextArea(StyleClassedTextArea area) {
        return new StyleClassedTextArea(area.getModel().getContent(), area.isPreserveStyle());
    }


    /* ********************************************************************** *
     *                                                                        *
     * CodeArea                                                               *
     *                                                                        *
     * ********************************************************************** */

    // CodeArea 1
    public static CodeArea codeArea() {
        return new CodeArea();
    }


    // CodeArea 2
    public static CodeArea codeArea(String text) {
        return new CodeArea(text);
    }

    // Clones CodeArea
    public static CodeArea cloneCodeArea(CodeArea area) {
        return new CodeArea(area.getModel().getContent());
    }

    /* ********************************************************************** *
     *                                                                        *
     * InlineCssTextArea                                                      *
     *                                                                        *
     * ********************************************************************** */

    // InlineCssTextArea 1
    public static InlineCssTextArea inlineCssTextArea() {
        return new InlineCssTextArea();
    }

    // InlineCssTextArea 2
    public static InlineCssTextArea inlineCssTextArea(String text) {
        return new InlineCssTextArea(text);
    }


    // Clones InlineCssTextArea
    public static InlineCssTextArea cloneInlineCssTextArea(InlineCssTextArea area) {
        return new InlineCssTextArea(area.getModel().getContent());
    }
}
