# AGENTS.md

## Project Goal
This project is a Minecraft clone written from scratch in **Java**.

## Key Difference from Minecraft
Unlike vanilla Minecraft (which uses 16×16×255 columnar chunks), this project uses **cubic chunks of size 16×16×16**.  
- This design allows **unbounded build height** in both upward and downward directions.
- All world storage, generation, and rendering should assume cubic chunks.

## Current State
- Core structure is being implemented.
- No strict coding conventions are enforced yet.

## Guidelines for Agents
- Assume Java is the implementation language.
- Any world, chunk, or entity system must support **cubic chunk storage**.
- Do not introduce assumptions about fixed world height limits.
