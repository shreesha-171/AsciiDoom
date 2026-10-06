# Ascii Doom

A lightweight, terminal-based 3D raycasting game written in Java using fixed-point arithmetic and JLine. Navigate through an ancient labyrinth, solve environmental gating mechanics, and locate the hidden relic to reveal your escape.

[![Open in GitHub Codespaces](https://img.shields.io/badge/Open_in-Codespaces-blue?style=for-the-badge&logo=github)](https://codespaces.new/shreesha-171/ascii-doom)
[![Run Application](https://img.shields.io/badge/Launch-Demo_Application-brightgreen?style=for-the-badge&logo=java)](https://github.com/shreesha-171/ascii-doom/releases/latest)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange?style=for-the-badge&logo=openjdk)](https://www.oracle.com/java/)
[![JLine 4](https://img.shields.io/badge/JLine-4.4.5-blue?style=for-the-badge)](https://github.com/jline/jline3)

or

**🚀 Live Application:** [https://shreesha-171.github.io/AsciiDoom](https://shreesha-171.github.io/AsciiDoom/)

## Game Rules & Objectives

The labyrinth is divided into two distinct sectors by a solid barrier. To successfully complete the game, you must complete the objectives in sequence:

1. **Obtain the Key (`◆`):** Explore the upper complex to locate the key.
2. **Breach the Door (`╬`):** Use the key to unlock the heavy iron door separating the upper and lower complexes.
3. **Locate the Relic (`?`):** Traverse the inner labyrinth of the lower sector. **The exit point remains hidden in 3D space until the relic is activated.**
4. **Claim the Treasure (`★`) *(Optional)*:** Search the lower complex for hidden treasure before making your getaway.
5. **Escape (`E`):** Navigate to the exit portal revealed in the bottom-right corner of the lower sector.

### Ending Outcomes
- **True Victory:** Escape with both the **Relic (`?`)** and **Treasure (`★`)**.
- **Pyrrhic Victory (*"Won, but at what cost?"*):** Escape with the **Relic (`?`)** while leaving the **Treasure (`★`)** behind.

## Controls

| Key | Action |
| :--- | :--- |
| **`W` / `S`** | Move Forward / Backward |
| **`A` / `D`** | Strafe Left / Right |
| **`J` / `L`** | Rotate View Left / Right |
| **`Q`** | Quit Game |

## ⚙️ Compilation & Running

### Prerequisites
* **JDK 17** or higher
* **JLine 4.4.5** JAR library (`jline-4.4.5.jar`) placed in your project root

### Direct Command Line (Recommended)

**Compile the source:**

javac --release 17 -cp "jline-4.4.5.jar" AsciiDoom.java

## Run the application:

Windows (Command Prompt / PowerShell):

DOS
java -cp ".;jline-4.4.5.jar" AsciiDoom
Linux / macOS:

Bash
java -cp ".:jline-4.4.5.jar" AsciiDoom

## Building with Maven

Structure your project directory as follows:

ascii-doom/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── AsciiDoom.java

Execute the build and run pipeline:

Bash
mvn clean compile exec:java
Architecture & Tech Stack
Language: Java 17

Rendering Engine: Custom DDA (Digital Differential Analysis) 3D Raycaster using 16.16 Fixed-Point Arithmetic.

Terminal Management: JLine 4 (Terminal Raw Mode & ANSI Escape Control).

### For "Launch Demo Application" Button:
Replace `YOUR_USERNAME` in the badge URL with your actual GitHub username:
```markdown
[![Run Application](https://img.shields.io/badge/Launch-Demo_Application-brightgreen?style=for-the-badge&logo=java)](https://github.com/YOUR_GITHUB_USERNAME/ascii-doom/releases/latest)
