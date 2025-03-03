package com.lxmusicmobile.lyric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricPlayer {
  final String timeExp = "^\\[([\\d:.]*)]";
//  HashMap tagRegMap;
  Pattern timePattern;

  String lyric = "";
  String translationLyric = "";
  List<HashMap> lines;
//  HashMap tags = null;
  boolean isPlay = false;
  int curLineNum = 0;
  int maxLine = 0;
  int offset = 150;
  boolean isOffered = false;
  long performanceTime = 0;
  int delay = 0;
  Object tid = null;
  boolean tempPause = false;
  boolean tempPaused = false;

  LyricPlayer() {
//    tagRegMap = new HashMap<String, String>();
//    tagRegMap.put("title", "ti");
//    tagRegMap.put("artist", "ar");
//    tagRegMap.put("album", "al");
//    tagRegMap.put("offset", "offset");
//    tagRegMap.put("by", "by");
//    tags = new HashMap();

    timePattern = Pattern.compile(timeExp);
  }

  public void setTempPause(boolean isPaused) {
    if (isPaused) {
      tempPause = true;
    } else {
      tempPause = false;
      if (tempPaused) {
        tempPaused = false;
        if (isPlay) refresh();
      }
    }
  }

//  @RequiresApi(api = Build.VERSION_CODES.N)
//  private void initTag() {
//    tagRegMap.forEach((tag, value) -> {
//      Pattern pattern = Pattern.compile("\\[" + value + ":([^\\]]*)]", Pattern.CASE_INSENSITIVE);
//      Matcher matcher = pattern.matcher(lyric);
//
//      tags.put(tag, matcher.group(1));
//    });
//  }

  private void startTimeout(Runnable runnable, long delay) {
    if (tid != null) Utils.clearTimeout(tid);
    tid = Utils.setTimeout(runnable, delay);
  }

  private void stopTimeout() {
    if (tid == null) return;
    Utils.clearTimeout(tid);
    tid = null;
  }

  private long getNow() {
    return System.nanoTime() / 1000000;
  }

  private int getCurrentTime() {
    return (int)(getNow() - this.performanceTime);
  }

  private void initLines() {
    String[] linesStr = lyric.split("\r\n|\n|\r");
    lines = new ArrayList<HashMap>();

    HashMap linesMap = new HashMap<String, HashMap>();
    HashMap timeMap = new HashMap<String, Integer>();

    for (String lineStr : linesStr) {
      String line = lineStr.trim();
      Matcher result = timePattern.matcher(line);
      if (result.find()) {
        String text = line.replaceAll(timeExp, "").trim();
        if (text.length() > 0) {
          String timeStr = result.group(1);
          if (timeStr == null) continue;
          String[] timeArr = timeStr.split(":");
          String hours;
          String minutes;
          String seconds;
          String milliseconds = "0";
          switch (timeArr.length) {
            case 3:
              hours = timeArr[0];
              minutes = timeArr[1];
              seconds = timeArr[2];
              break;
            case 2:
              hours = "0";
              minutes = timeArr[0];
              seconds = timeArr[1];
              break;
            default:
              return;
          }
          if (seconds.contains(".")) {
            timeArr = seconds.split("\\.");
            seconds = timeArr[0];
            milliseconds = timeArr[1];
          }
          HashMap<String, Object> lineInfo = new HashMap<String, Object>();
          int time = Integer.parseInt(hours) * 60 * 60 * 1000
            + Integer.parseInt(minutes) * 60 * 1000
            + Integer.parseInt(seconds) * 1000
            + Integer.parseInt(milliseconds);
          lineInfo.put("time", time);
          lineInfo.put("text", text);
          lineInfo.put("translation", "");
          timeMap.put(timeStr, time);
          linesMap.put(timeStr, lineInfo);
        }
      }
    }

    String[] translationLines = translationLyric.split("\r\n|\n|\r");
    for (String translationLine : translationLines) {
      String line = translationLine.trim();
      Matcher result = timePattern.matcher(line);
      if (result.find()) {
        String text = line.replaceAll(timeExp, "").trim();
        if (text.length() > 0) {
          String timeStr = result.group(1);
          HashMap targetLine = (HashMap) linesMap.get(timeStr);
          if (targetLine != null) targetLine.put("translation", text);
        }
      }
    }

    Set<Entry<String, Integer>> set = timeMap.entrySet();
    List<Entry<String, Integer>> list = new ArrayList<Entry<String, Integer>>(set);
    Collections.sort(list, new Comparator<Entry<String, Integer>>() {
      public int compare(Map.Entry<String, Integer> o1,
                         Map.Entry<String, Integer> o2) {
        return o1.getValue().compareTo(o2.getValue());
      }
    });

    lines = new ArrayList<HashMap>(list.size());
    for (Entry<String, Integer> entry : list) {
      lines.add((HashMap) linesMap.get(entry.getKey()));
    }

    this.maxLine = lines.size() - 1;
  }

  private void  init() {
    if (lyric == null) lyric = "";
    if (translationLyric == null) translationLyric = "";
//    initTag();
    initLines();
    onSetLyric(lines);
  }

  public void pause() {
    if (!isPlay) return;
    isPlay = false;
    isOffered = false;
    tempPaused = false;
    stopTimeout();
    if (curLineNum == maxLine) return;
    int curLineNum = this.findCurLineNum(getCurrentTime());
    if (this.curLineNum != curLineNum) {
      this.curLineNum = curLineNum;
      this.onPlay(curLineNum);
    }
  }

  public void play(int curTime) {
    if (this.lines.size() == 0) return;
    pause();
    isPlay = true;

    performanceTime = getNow() - (long)curTime;

    curLineNum = findCurLineNum(curTime) - 1;

    refresh();
  }

  private int findCurLineNum(int curTime) {
    int length = lines.size();
    for (int index = 0; index < length; index++) {
      if (curTime <= (int) ((HashMap)lines.get(index)).get("time")) return index == 0 ? 0 : index - 1;
    }
    return length - 1;
  }

  private void handleMaxLine() {
    this.onPlay(this.curLineNum);
    this.pause();
  }

  private void refresh() {
    if (tempPaused) tempPaused = false;
    // Log.d("Lyric", "refresh: " + curLineNum);

    curLineNum++;
    if (curLineNum >= maxLine) {
      handleMaxLine();
      return;
    }
    HashMap curLine = lines.get(curLineNum);

    int currentTime = getCurrentTime();
    int driftTime = currentTime - (int) curLine.get("time");
    // Log.d("Lyric", "driftTime: " + driftTime);

    if (driftTime >= 0 || curLineNum == 0) {
      HashMap nextLine = lines.get(curLineNum + 1);
      delay = (int) nextLine.get("time") - (int) curLine.get("time") - driftTime;
      // Log.d("Lyric", "delay: " + delay + "  driftTime: " + driftTime);
      if (delay > 0) {
        if (!isOffered && delay >= offset) {
          delay -= offset;
          isOffered = true;
        }
        if (isPlay) {
          startTimeout(() -> {
            if (tempPause) {
              tempPaused = true;
              return;
            }
            refresh();
          }, delay);
        }
        onPlay(curLineNum);
        return;
      }
    }

    curLineNum = this.findCurLineNum(currentTime) - 1;
    refresh();
  }

  public void setLyric(String lyric, String translationLyric) {
    if (isPlay) pause();
    this.lyric = lyric;
    this.translationLyric = translationLyric;
    init();
  }

  public void onPlay(int lineNum) {}

  public void onSetLyric(List lines) {}

}
