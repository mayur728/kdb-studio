package studio;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.core.KeyPressInfo;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JFileChooserFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.After;
import org.junit.Test;
import studio.core.Studio;

import static org.assertj.swing.core.matcher.DialogMatcher.withTitle;
import static org.assertj.swing.edt.GuiActionRunner.execute;
import static org.assertj.swing.finder.JFileChooserFinder.findFileChooser;
import static org.assertj.swing.finder.WindowFinder.findFrame;
import static org.junit.Assert.*;


public class StudioPanelTest extends AssertJSwingJUnitTestCase {

    private FrameFixture frame;

    @Override
    protected void onSetUp() {
        try {
            Path tempDirectory = Files.createTempDirectory(System.getProperty("user.name"));
            System.setProperty("user.home", tempDirectory.toString());
            Studio.init0();
            execute(() -> Studio.createPanel(new String[0]));
            frame = findFrame(new GenericTypeMatcher<Frame>(Frame.class) {

                @Override
                protected boolean isMatching(Frame frame) {
                    return "Script0 @9989 Studio for kdb+ dz2.0".equals(frame.getTitle()) && frame.isShowing();
                }
            }).using(robot());
            frame.show(); // shows the frame to test
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testNewShortcuts() {

        JTextComponentFixture textComponent = frame.textBox("qEditor");
        textComponent.requireEmpty();
        textComponent.setText("ccabsfs\ngdvcv");

        assertEquals(textComponent.text(), "ccabsfs\ngdvcv");

        //clean Action
        textComponent.pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_C).modifiers(KeyEvent.CTRL_MASK, KeyEvent.SHIFT_MASK));
        DialogFixture dialogClean = frame.dialog(withTitle("Save changes?"));
        //Assertions
        assertTrue("Dialog window should be triggered", dialogClean.isEnabled());
        dialogClean.close();

        //close file
        textComponent.pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_W).modifiers(KeyEvent.CTRL_MASK, KeyEvent.SHIFT_MASK));
        DialogFixture dialogClose = frame.dialog(withTitle("Save changes?"));
        //Assertions
        assertTrue(dialogClose.isEnabled());
        dialogClose.close();

        //test editServerAction
        textComponent.pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_E).modifiers(KeyEvent.CTRL_MASK, KeyEvent.ALT_MASK));
        DialogFixture dialogEdit = frame.dialog(withTitle("Edit Server Details"));
        //Assertions
        assertTrue(dialogEdit.isEnabled());
        dialogEdit.close();

        //test addServerAction
        textComponent.pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_A).modifiers(KeyEvent.CTRL_MASK, KeyEvent.ALT_MASK));
        DialogFixture dialogAdd = frame.dialog(withTitle("Add a new server"));
        //Assertions
        assertTrue(dialogAdd.isEnabled());
        dialogAdd.close();

        //test removeServerAction
        textComponent.pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_R).modifiers(KeyEvent.CTRL_MASK, KeyEvent.ALT_MASK));
        DialogFixture dialogRemove = frame.dialog(withTitle("Remove server?"));
        //Assertions
        assertTrue(dialogRemove.isEnabled());
        dialogRemove.close();

        //test saveAsFileAction
        textComponent.pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_S).modifiers(KeyEvent.SHIFT_MASK, KeyEvent.ALT_MASK));
        JFileChooserFixture fileChooserSaveAs = findFileChooser().using(frame.robot());
        //Assertions
        assertTrue(fileChooserSaveAs.target().isShowing());
        assertEquals(fileChooserSaveAs.target().getDialogTitle(), "Save script as");
        fileChooserSaveAs.cancel();

        //exit Action
        textComponent.pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_X).modifiers(KeyEvent.ALT_MASK, KeyEvent.SHIFT_MASK));
        DialogFixture dialogExit = frame.dialog(withTitle("Save changes?"));
        //Assertions
        assertTrue(dialogExit.isEnabled());
        dialogExit.close();

        //Settings dialog
        textComponent.pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_S).modifiers(KeyEvent.CTRL_MASK, KeyEvent.ALT_MASK));
        DialogFixture dialogSettings = frame.dialog(withTitle("Settings"));
        //Assertions
        assertTrue(dialogSettings.isEnabled());
        dialogSettings.close();

        //Theme dialog
        textComponent.pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_T).modifiers(KeyEvent.CTRL_MASK, KeyEvent.SHIFT_MASK));
        DialogFixture dialogTheme = frame.dialog(withTitle("Theme"));
        //Assertions
        assertTrue(dialogTheme.isEnabled());
        dialogTheme.close();

    }

    @After
    public void onTearDown() {
       frame.cleanUp();
    }

}
