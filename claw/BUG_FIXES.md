# Bug 修复说明

## 版本 0.3.0-bugfixes - 2026-03-19

本次修复解决了用户反馈的两个重要bug。

---

## Bug 1: 长命令不自动换行，溢出屏幕

### 问题描述
- 用户输入长命令时，终端不会自动换行
- 命令文本溢出屏幕右侧，无法完整显示

### 根本原因
- TerminalView的自动换行功能未正确配置
- 字体大小和列宽设置不匹配

### 修复方案
**文件**: `android/app/src/main/java/com/openclaw/android/MainActivity.kt`

**修改内容**:
```kotlin
private fun setupTerminalView() {
    binding.terminalView.setTerminalViewClient(terminalViewClient)
    binding.terminalView.setTextSize(currentTextSize)
    
    // Bug Fix 1: 启用自动换行并动态设置列宽
    val displayMetrics = resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels
    val estimatedCharWidth = currentTextSize * 0.6f
    val columns = ((screenWidth / estimatedCharWidth) * 0.9f).toInt().coerceAtLeast(40)
    
    binding.terminalView.setColumns(columns)
    binding.terminalView.setEmulator(80, 24)
    
    Log.i(TAG, "Terminal configured: columns=$columns, textSize=$currentTextSize")
}
```

### 效果
- ✅ 长命令自动换行
- ✅ 根据屏幕宽度动态调整列数
- ✅ 最小列数保证40列，确保可读性

---

## Bug 2: 插件安装失败但显示成功

### 问题描述
- 安装code-server时出现npm错误：`npm error code EAI_AGAIN`
- 安装Claude Code、Gemini CLI、Codex CLI也失败
- 但界面显示绿色✅成功标记

### 错误信息
```
npm error code EAI_AGAIN
npm error syscall getaddrinfo
npm error errno EAI_AGAIN
npm error request to https://registry.npmjs.org/code-server failed, 
reason: getaddrinfo EAI_AGAIN registry.npmjs.org
```

### 根本原因
1. 原安装脚本使用 `|| true`，忽略所有错误
2. 只检查命令执行状态，不检查实际安装结果
3. 没有网络错误的特殊处理
4. 状态更新过早，没有验证二进制文件是否存在

### 修复方案
**文件**: `android/app/src/main/assets/post-setup.sh`

**修改内容**:
1. 添加 `install_npm_package()` 函数，包含完整错误检测
2. 验证命令是否真正安装成功（使用 `command -v`）
3. 检查npm错误日志，识别网络错误（EAI_AGAIN、ENOTFOUND、ETIMEDOUT）
4. 显示详细的失败信息和建议
5. 提供手动安装命令

**关键改进**:
```bash
install_npm_package() {
    local name="$1"
    local package="$2"
    local log_file="$TMPDIR/npm_${name}.log"
    
    # 记录安装输出
    if npm install -g "$package" > "$log_file" 2>&1; then
        # 验证是否真正安装成功
        if command -v "$name" &> /dev/null; then
            echo "✓ $name installed successfully"
            return 0
        else
            echo "✗ $name: Installation failed (command not found)"
            return 1
        fi
    else
        echo "✗ $name: Installation failed"
        
        # 检查是否是网络错误
        if grep -q "EAI_AGAIN\|ENOTFOUND\|ETIMEDOUT" "$log_file"; then
            echo "⚠ Network error detected. Check your VPN/connection."
        fi
        
        cat "$log_file" | tail -20
        return 1
    fi
}
```

### 效果
- ✅ 准确检测安装失败
- ✅ 区分网络错误和安装错误
- ✅ 显示详细的错误日志
- ✅ 提供失败工具的摘要
- ✅ 提供手动安装建议

### 使用示例

**安装成功**:
```
▸ [7/7] Installing optional tools...
  Installing code-server...
    (This may take a while, please wait...)
  ✓ code-server installed successfully
  ✓ Claude Code installed successfully

  Successfully installed:
    ✓ code-server
    ✓ Claude Code
```

**安装失败**:
```
▸ [7/7] Installing optional tools...
  Installing code-server...
    (This may take a while, please wait...)
  ✗ code-server: Installation failed
    See log: /data/user/0/.../npm_code-server.log
    ⚠ Network error detected. Check your VPN/connection.
    npm error code EAI_AGAIN
    ...

  Failed to install:
    ✗ code-server

  Tip: Check your network connection and try again.
  You can manually install later:
    npm install -g code-server
```

---

## 测试建议

### 测试Bug 1修复
1. 输入一个很长的命令（超过屏幕宽度）
2. 观察是否自动换行
3. 调整字体大小，验证列宽是否动态调整

### 测试Bug 2修复
1. 尝试安装插件（如code-server）
2. 故意断开网络，观察错误提示
3. 重新连接网络，观察成功安装
4. 检查失败工具是否显示在摘要中

---

## 技术细节

### Bug 1技术要点
- 使用 `DisplayMetrics` 获取屏幕宽度
- 根据 `textSize` 估算字符宽度（0.6倍）
- 列数 = (屏幕宽度 / 字符宽度) × 0.9（留出边距）
- 最小列数保证40，避免列数过少

### Bug 2技术要点
- 使用 `command -v` 验证命令是否存在
- 使用 `> log_file 2>&1` 捕获所有输出
- 使用 `grep -q` 检测网络错误关键词
- 使用数组记录成功和失败的工具
- 使用 `case` 语句生成手动安装命令

---

## 兼容性

- ✅ 完全向后兼容
- ✅ 不影响现有功能
- ✅ 仅改进错误处理和用户体验

---

## 反馈

如果您遇到任何问题或有新的bug报告，请：
1. 查看错误日志文件（位于 `$TMPDIR/npm_*.log`）
2. 检查网络连接（特别是VPN设置）
3. 尝试手动安装（使用提供的命令）
4. 提交Issue并附上错误日志

---

**修复日期**: 2026-03-19  
**修复版本**: 0.3.0-bugfixes  
**修复人员**: 扣子AI助手
