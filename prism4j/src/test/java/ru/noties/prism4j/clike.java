package ru.noties.prism4j;

import android.support.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

@RunWith(Parameterized.class)
public class clike {

    @Parameterized.Parameters(name = "{0}")
    @NonNull
    public static Collection<Object> parameters() {
        return TestUtils.testFiles("clike");
    }

    private Prism4j prism4j;

    @Before
    public void before() {
        prism4j = new Prism4j();
    }

    private String file;

    public clike(@NonNull String file) {
        this.file = file;
    }

    @Test
    public void test() {
        final TestUtils.Case c = TestUtils.readCase(file);
        TestUtils.assertCase(c, prism4j.tokenize(c.input, Grammars.clike()));
    }
}
