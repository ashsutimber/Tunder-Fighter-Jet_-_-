import Controller.CoreController.GameEngine;

public class Main {

    /**
     * 应用程序主入口。
     *
     * <p>使用 {@link javax.swing.SwingUtilities#invokeLater(Runnable) SwingUtilities.invokeLater()}
     * 将游戏启动逻辑调度到 AWT 事件分发线程（EDT），确保所有 Swing GUI
     * 操作都在正确的线程上执行。</p>
     *
     * <p>启动后用户将依次经历以下流程：</p>
     * <ol>
     *   <li><b>标题画面</b> — 显示操作说明，按空格进入飞机选择</li>
     *   <li><b>飞机选择</b> — 从 4 种飞机中选择 1 架（雷霆/疾风/破甲/追猎）</li>
     *   <li><b>游戏进行</b> — 7 轮波次战斗，含 2 场 Boss 战</li>
     *   <li><b>结束画面</b> — Game Over 或通关，按空格可重新选择飞机</li>
     * </ol>
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        // 使用 Swing 事件分发线程（EDT）启动游戏，确保 GUI 线程安全
        javax.swing.SwingUtilities.invokeLater(() -> {
            // 创建游戏引擎实例
            Controller.CoreController.GameEngine engine = new Controller.CoreController.GameEngine();
            // 启动游戏：初始化窗口、注册输入监听、启动 60FPS 主循环
            engine.start();
        });
    }
}
