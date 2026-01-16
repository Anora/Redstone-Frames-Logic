📘 Redstone Frames & Logic (RFL)

Redstone Frames & Logic (RFL) is a NeoForge Minecraft mod focused on advanced redstone logic blocks and frame-based systems, inspired by classic mods like RedPower — but rebuilt with modern Minecraft internals and a custom logic network.

This mod introduces deterministic logic nodes, configurable timing, and non-vanilla redstone behavior that goes beyond what standard repeaters and comparators can do.

✨ Current Features
🔁 Logic Network Core

Custom logic node system (not vanilla redstone ticking)

Deterministic, step-based evaluation

Clean separation between:

Minecraft blocks (I/O)

Logic nodes (pure logic)

🔴 RFL Repeater

Ground-placed logic block

Back = input, Front = output

Configurable delay steps inspired by RedPower:

1, 2, 3, 4, 8, 16, 32, 64, 128 ticks


Right-click to cycle delay

Correct redstone output behavior (no ghost power)

Persists delay settings across world reloads

🔁 NOT Gate (Inverter)

Uses InverterNode

Single input → inverted output

Integrates into the same logic network as the repeater

Works across chunk unloads and world reloads

🧠 Architecture Overview

RFL is built around a node-based logic graph:

Blocks do not contain logic

Blocks only:

Read redstone input

Push signals into the network

Reflect node output back into the world

Logic is handled by reusable node classes (AND, OR, NOT, latches, timers, etc.)

This allows:

Complex logic without block entities

Predictable timing

Easier expansion to bundled signals and frames

🧩 Planned Features

Additional logic gates:

AND, OR, NAND, NOR, XOR

Stateful logic:

RS Latches

D Flip-Flops

Toggle latches

Bundled / multi-channel redstone

Frame-based movement and control logic

Logic probes and debugging tools

⚙️ Development Status

Active development
This mod is still early-stage and APIs may change.

The current focus is:

Exposing existing logic nodes as blocks

Stabilizing redstone I/O semantics

Expanding the logic block set

🛠️ Development Environment

Minecraft 1.21.2

Mod Loader: NeoForge 21.2.x

Java 21

Mappings: Parchment

Built with NeoGradle

📜 License

This project is licensed under the GNU Lesser General Public License v3.0 (LGPL-3.0).

You are free to use, modify, and distribute this mod, including in closed-source projects.
However, any modifications made directly to Redstone Frames & Logic (RFL) itself must be
released under the same LGPL-3.0 license.

❤️ Credits & Inspiration

RedPower (Eloraam)

Modern redstone logic mods

NeoForge community