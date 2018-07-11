package ru.noties.prism4j.languages;

import android.support.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import ru.noties.prism4j.Prism4j;
import ru.noties.prism4j.TestUtils;
import ru.noties.prism4j.annotations.PrismBundle;

@RunWith(Parameterized.class)
@PrismBundle(include = "clike", name = ".GrammarLocatorClike")
public class Test_clike {

    @Parameterized.Parameters(name = "{0}")
    @NonNull
    public static Collection<Object> parameters() {
        return TestUtils.testFiles("clike");
    }

    private Prism4j prism4j;

    @Before
    public void before() {
        prism4j = new Prism4j(new GrammarLocatorClike());
    }

    private String file;

    public Test_clike(@NonNull String file) {
        this.file = file;
    }

    @Test
    public void test() {
        final TestUtils.Case c = TestUtils.readCase(file);
        TestUtils.assertCase(c, prism4j.tokenize(c.input, prism4j.grammar("clike")));
    }
}
