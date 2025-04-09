package weka.gui;

import weka.core.Defaults;
import weka.core.Settings;

import java.util.List;

/**
 * Inner class for defaults
 */
public final class GUIChooserDefaults extends Defaults {

    /** APP name (GUIChooser isn't really an "app" as such */
    public static final String APP_NAME = "GUIChooser";

    /** ID */
    public static final String APP_ID = "guichooser";

    /** Settings key for LAF */
    protected static final Settings.SettingKey LAF_KEY =
            new Settings.SettingKey(APP_ID + ".lookAndFeel", "Look and feel for UI",
                    "Note: a restart is required for this setting to come into effect");

    /** Default value for LAF */
    protected static final String LAF =
            "com.formdev.flatlaf.FlatLightLaf";

    private static final long serialVersionUID = -8524894440289936685L;

    /**
     * Constructor
     */
    public GUIChooserDefaults() {
        super(APP_ID);
        List<String> lafs = LookAndFeel.getAvailableLookAndFeelClasses();
        lafs.add(0, "<use platform default>");
        LAF_KEY.setPickList(lafs);
        m_defaults.put(LAF_KEY, LAF);
    }
}
