# 人工智能聊天模型集成库

一个Java库，用于集成AI聊天模型，提供标准化接口以与各种聊天模型提供商交互。

## 特性
- 标准化的`ChatModel`接口，确保一致的AI模型交互体验
- 支持提示词构建和响应处理
- 使用Jackson进行JSON序列化/反序列化
- 使用JUnit和Mockito进行全面的单元测试

## 项目结构
```
src/
├── main/
│   ├── java/
│   │   └── com/github/aimodel/
│   │       ├── chat/model/       # 聊天模型接口定义
│   │       │   ├── ChatModel.java        # 核心聊天模型接口
│   │       │   └── ChatResponse.java     # 模型响应封装类
│   │       ├── message/          # 消息模型定义
│   │       ├── prompt/           # 提示词构建工具
│   │       └── model/            # 基础模型接口
│   └── resources/                # 资源文件
└── test/
    └── java/com/github/aimodel/
        └── chat/                    # 聊天模型测试
            └── ChatModelTests.java  # 核心接口测试类
```
**目录说明**：
- `chat/model`: 定义聊天模型核心接口及响应结构
- `message`: 封装用户消息、助手消息等消息类型
- `prompt`: 提供提示词构建和格式化工具
- `resources`: 包含应用资源文件
- `test`: 包含与主代码结构对应的单元测试

## 安装
在Maven项目中添加以下依赖：
```xml
<dependency>
    <groupId>com.github.aimodel</groupId>
    <artifactId>ai-model</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## 使用示例

### 初始化聊天模型
以下代码展示如何初始化火山引擎聊天模型的匿名内部类实现：
```java
// 初始化火山引擎聊天模型（匿名内部类实现示例）
ChatModel chatModel = new ChatModel() {
    @Override
    public ChatResponse call(Prompt prompt) {
        // 火山引擎API调用实现逻辑
        return new ChatResponse(new Generation(new AssistantMessage("模拟火山引擎响应: " + prompt.getContents())));
    }
};
```

### 构建多消息提示词
通过Prompt类可以创建包含系统消息和用户消息的复杂对话提示：
```java
// 使用Prompt类构建多消息提示
Prompt multiMessagePrompt = new Prompt(
    Arrays.asList(
        new SystemMessage("你是一个AI助手，用简洁的语言回答问题。"),
        new UserMessage("什么是人工智能?")
    )
);
ChatResponse response1 = chatModel.call(multiMessagePrompt);
System.out.println("多消息提示响应: " + response1);
```

### 创建带变量的模板提示词
PromptTemplate支持通过变量动态生成提示内容，适合复用提示结构：
```java
// 使用PromptTemplate创建带变量的模板提示
PromptTemplate template = new PromptTemplate(
    "系统提示: {system_prompt}\n用户问题: {user_question}"
);
template.add("system_prompt", "你是一个数学专家，解决用户的数学问题。");
template.add("user_question", "计算1+2*3的结果。");
Prompt templatePrompt = template.create();
ChatResponse response2 = chatModel.call(templatePrompt);
System.out.println("模板提示响应: " + response2);
```

### 增强方法修改消息
Prompt类提供augment方法，可以方便地修改现有提示中的系统消息和用户消息：
```java
// 使用Prompt的增强方法修改消息
Prompt augmentedPrompt = multiMessagePrompt
    .augmentSystemMessage("你是一个AI助手，用简洁的中文回答问题。")
    .augmentUserMessage("什么是机器学习，它和人工智能有什么关系?");
ChatResponse response3 = chatModel.call(augmentedPrompt);
System.out.println("增强提示响应: " + response3);
```


## 测试
使用Maven运行测试套件：
```bash
mvn test
```

## 许可证
本项目采用Apache License 2.0许可证 - 详见LICENSE文件。
