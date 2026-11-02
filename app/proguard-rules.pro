# Aurora TV keeps no reflection-based code. Media3 ships consumer rules.
-dontwarn org.checkerframework.**

# Custom view inflated from XML by name.
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
