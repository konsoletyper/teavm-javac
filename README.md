## ABANDONED 

This project requires a lot of efforts from my side which I don't have and (comparing to TeaVM itself) I don't see any viable use-cases for me.  May be I'll revive it sometimes. You can fork, copy code from it, but PLEASE, DON'T ASK ME HOW TO CONTINUE WORKING ON IT. If you are really interested, please, dig into how TeaVM works internally and how to make it boostrappable, I won't provide any help.

---

An offline Java compiler that runs in the browser.

This is merely javac from OpenJDK compiled with [TeaVM](http://teavm.org).

See live example [here](http://teavm.org/sandbox/).

## Building

```
$ mvn package
```

Resulting `.war` file can be found in `compiler/target/`.
Deploy it on Tomcat, or simply unpack all resources and serve them via HTTP. 
