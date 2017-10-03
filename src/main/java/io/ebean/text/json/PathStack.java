package io.ebean.text.json;

import io.ebean.util.ArrayStack;

public class PathStack extends ArrayStack<String> {

  public String peekFullPath(String key) {

    String prefix = peekWithNull();
    if (prefix != null) {
      return prefix + "." + key;
    } else {
      return key;
    }
  }

  public void pushPathKey(String key) {

    String prefix = peekWithNull();
    if (prefix != null) {
      key = prefix + "." + key;
    }
    push(key);
  }

}
