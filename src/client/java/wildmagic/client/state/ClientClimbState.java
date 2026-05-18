package wildmagic.client.state;

public final class ClientClimbState {
    private static boolean climbing = false;

    private ClientClimbState() {}

    public static boolean isClimbing() { return climbing; }
    public static void setClimbing(boolean value) { climbing = value; }
}