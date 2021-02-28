#version 300 es
precision mediump float;

uniform sampler2D uTexture;

in vec2 textureCoord;
out vec4 fragColor;

void main() {
    fragColor = texture(uTexture, textureCoord);
}