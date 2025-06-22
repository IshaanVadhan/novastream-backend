package com.novastream;

import com.novastream.config.ServerControlGUI;

public class GUILauncher {

  public static void main(String[] args) {
    System.setProperty("java.awt.headless", "false");
    ServerControlGUI.launchWithSplash();
  }
}
