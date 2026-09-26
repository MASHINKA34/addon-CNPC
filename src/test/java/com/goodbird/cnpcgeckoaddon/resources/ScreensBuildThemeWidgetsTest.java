package com.goodbird.cnpcgeckoaddon.resources;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the addon's screens to the theme's widgets.
 *
 * <p>A screen that builds CustomNPCs' own button, field or label draws that one widget in the
 * CustomNPCs look in the middle of a themed panel, and the stock label's dark grey all but
 * vanishes on the theme's dark one. The theme's classes are CustomNPCs' with the same
 * constructors, so building them costs a screen nothing - which is also why nothing else would
 * notice the day one is left out.</p>
 */
class ScreensBuildThemeWidgetsTest {

    private static final Path GUI = Path
            .of(System.getProperty("cnpcgeckoaddon.projectDir", "."))
            .resolve(Path.of("src", "main", "java", "com", "goodbird", "cnpcgeckoaddon", "client", "gui"));

    /** The theme's own classes, which extend these and so are the one place that names them. */
    private static final Path THEME = GUI.resolve("theme");

    private static final Pattern STOCK_WIDGET =
            Pattern.compile("\\bnew\\s+(GuiButtonNop|GuiButtonYesNo|GuiTextFieldNop|GuiLabel)\\s*\\(");
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    @Test
    @DisplayName("no screen of the addon builds CustomNPCs' button, yes/no, field or label instead of the theme's")
    void everyScreenBuildsThemeWidgets() throws IOException {
        Set<String> offenders = new TreeSet<>();
        int screens = 0;
        try (Stream<Path> sources = Files.walk(GUI)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")
                    && !path.startsWith(THEME)).toList()) {
                screens++;
                String code = BLOCK_COMMENT.matcher(Files.readString(source, StandardCharsets.UTF_8)).replaceAll(" ");
                Matcher matcher = STOCK_WIDGET.matcher(LINE_COMMENT.matcher(code).replaceAll(" "));
                while (matcher.find()) {
                    offenders.add(source.getFileName() + ": new " + matcher.group(1));
                }
            }
        }
        assertTrue(screens > 100, "only " + screens + " sources were found under " + GUI);
        assertTrue(offenders.isEmpty(), "build ThemeButton, ThemeYesNo, ThemeTextField or ThemeLabel instead: "
                + offenders);
    }
}
