# question and answer

## not found suitable agent

question: http://localhost:8900/chat?userId=1&sessionId=2&prompts=hello,%20Master%20Yoda!
answer: Hello there! How can I assist you today?

question: http://localhost:8900/completed?userId=1&sessionId=2&prompts=hello,%20Master%20Yoda!
answer: hello, Master Yoda!

## found agent: echoAgent

question: http://localhost:8900/chat?userId=1&sessionId=2&prompts=please%20just%20echo:%20hello,%20Master%20Yoda!
answer: I'm echo agent! echo: please just echo: hello, Master Yoda

question: http://localhost:8900/chat?userId=1&sessionId=2&prompts=please%20just%20echo:%20hello,%20Master%20Yoda!
answer:

- sse message: 1
- sse message: 2
- sse message: 3
- sse message: 4
- sse message: 5
- sse message: 6
- sse message: 7
- sse message: 8
- sse message: 9
- sse message: 10

## found agent: mathAgent

question: http://localhost:8900/chat?userId=1&sessionId=2&prompts=calculate%20the%20square%20root%20of%2013,%20and%20keep%208%20precision
answer: 3.60555128

question: http://localhost:8900/completed?userId=1&sessionId=2&prompts=calculate%20the%20square%20root%20of%2013,%20and%20keep%208%20precision
answer:
注：LangChain4j流式模型如果使用tool，返回结果为空？

