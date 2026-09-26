import dev.cobra.client.core.ScreenRecorder;
import dev.cobra.client.core.module.Features;
import java.io.File;
public class Regression {
  static void check(boolean v, String m) { if (!v) throw new AssertionError(m); }
  public static void main(String[] args) throws Exception {
    Features.MotionBlur a = new Features.MotionBlur(), b = new Features.MotionBlur();
    long t = 1_000_000_000L;
    check(a.frameAt(0,0,70,1.7,0,t)==0x00808000,"initial frame");
    int still=a.frameAt(0,0,70,1.7,0,t+16_666_667L);
    check((still & 0xFFFFFF)==0x808000,"stationary must encode exact neutral");
    a.reset(); b.reset();
    a.frameAt(0,0,70,1.7,0,t); b.frameAt(0,0,70,1.7,0,t);
    int x=0,y=0;
    for(int i=1;i<=60;i++) x=a.frameAt(i*2,0,70,1.7,0,t+i*16_666_667L);
    for(int i=1;i<=120;i++) y=b.frameAt(i,0,70,1.7,0,t+i*8_333_333L);
    check(Math.abs(((x>>16)&255)-((y>>16)&255))<=1,"frame-rate independent turn blur");
    check(a.frameAt(200,0,70,1.7,0,t+3_000_000_000L)==0x00808000,"reset after stall");
    File dir=new File("checks/recordings"); dir.mkdirs();
    ScreenRecorder r=new ScreenRecorder();
    r.start(30); r.offer(new int[321*241],321,241,"ffmpeg",dir,"Window size","Balanced");
    Thread.sleep(350);
    r.offer(new int[400*300],400,300,"ffmpeg",dir,"Window size","Balanced");
    Thread.sleep(200); r.togglePause(); Thread.sleep(60); r.togglePause(); Thread.sleep(150);
    File first=r.stop();
    r.start(60); r.offer(new int[320*240],320,240,"ffmpeg",dir,"Window size","Balanced");
    Thread.sleep(350); File second=r.stop(); Thread.sleep(700);
    check(r.takeError()==null,"recording should not fail");
    check(first.length()>0 && second.length()>0 && !first.equals(second),"restart must produce separate files");
    System.out.println("PASS: motion neutral, FPS equivalence, stall reset; recording odd dimensions, resize, pause and immediate restart");
    System.out.println(first); System.out.println(second);
  }
}
