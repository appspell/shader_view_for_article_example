#version 300 es

precision mediump float;

uniform vec4 uMyUniform;

in vec2 textureCoord;
out vec4 fragColor;

void main() {
    fragColor = vec4(textureCoord.x, textureCoord.y, 1.0, 1.0) * uMyUniform;
}