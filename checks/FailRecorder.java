import dev.cobra.client.core.ScreenRecorder;
import java.io.*;
public class FailRecorder {
 public static void main(String[] args) throws Exception {
  File fake = new File("checks/fail-ffmpeg.sh").getAbsoluteFile();
  try (FileWriter w=new FileWriter(fake)) { w.write("#!/bin/sh\necho 'encoder unavailable' >&2\nexit 1\n"); } fake.setExecutable(true);
  ScreenRecorder r=new ScreenRecorder(); r.start(30);
  r.offer(new int[321*241],321,241,fake.toString(),new File("checks/failure"),"Window size","High");
  Thread.sleep(300);
  if(r.state()!=ScreenRecorder.State.IDLE) throw new AssertionError("Must exit recording state after pipe failure");
  String err=r.takeError();
  if(err==null || !err.contains("encoder unavailable")) throw new AssertionError("Must expose encoder diagnostic: "+err);
  System.out.println("PASS: encoder failure returns to IDLE and reports the actual diagnostic");
 }
}
