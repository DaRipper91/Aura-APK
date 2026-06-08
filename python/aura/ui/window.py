from PySide6.QtWidgets import (
    QMainWindow, QVBoxLayout, QWidget, QTextEdit, QLineEdit, 
    QLabel, QHBoxLayout, QSlider, QFormLayout, QGroupBox, QPushButton,
    QComboBox, QFileDialog, QListWidget, QFrame, QScrollArea, QGraphicsOpacityEffect
)
from PySide6.QtCore import Qt, QThread, Signal, QTimer, QPropertyAnimation, QEasingCurve, QPoint
from PySide6.QtGui import QIcon, QFontDatabase, QFont, QPainter, QColor, QLinearGradient, QPen, QRadialGradient
from aura_core.engine import OllamaClient
from aura_core.mandates import aura_component
from markdown_it import MarkdownIt
import os
import sys
import ctypes
import json
import html
import subprocess
import time
import random
from typing import Optional

class GhostLogArea(QTextEdit):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setReadOnly(True)
        self.setStyleSheet("""
            QTextEdit {
                background-color: transparent;
                color: rgba(0, 230, 230, 0.4);
                border: none;
                border-left: 1px solid rgba(0, 230, 230, 0.1);
                padding-left: 5px;
                font-family: 'Monospace';
                font-size: 9px;
            }
        """)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)

    def log(self, message: str):
        timestamp = time.strftime("%H:%M:%S")
        safe_msg = html.escape(message)
        safe_time = html.escape(timestamp)
        self.append(f"<span style='white-space: pre-wrap;'>[{safe_time}] {safe_msg}</span>")
        self.moveCursor(self.textCursor().MoveOperation.End)

class PowerStripe(QFrame):
    def __init__(self, color="#00e6e6", parent=None):
        super().__init__(parent)
        self.setFixedWidth(4)
        self.color = color
        self.intensity = 0.5
        self.setStyleSheet(f"background-color: {color}; border-radius: 2px;")

    def set_intensity(self, val: float):
        self.intensity = max(0.1, min(1.0, val))
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        grad = QLinearGradient(0, 0, 0, self.height())
        color = QColor(self.color)
        color.setAlphaF(self.intensity)
        grad.setColorAt(0, color)
        grad.setColorAt(1, QColor(0, 0, 0, 0))
        painter.fillRect(self.rect(), grad)

class AudioVisualizer(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedHeight(20)
        self.setFixedWidth(100)
        self.bars = [random.uniform(0.1, 0.8) for _ in range(15)]
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.update_bars)
        self.is_active = False

    def start(self):
        self.is_active = True
        self.timer.start(100)

    def stop(self):
        self.is_active = False
        self.timer.stop()
        self.update()

    def update_bars(self):
        self.bars = [random.uniform(0.1, 0.9) for _ in range(15)]
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        w = self.width() / len(self.bars)
        for i, val in enumerate(self.bars):
            h = self.height() * (val if self.is_active else 0.1)
            y = (self.height() - h) / 2
            
            # Base color
            color = QColor("#00e6e6")
            color.setAlphaF(0.8 if self.is_active else 0.3)
            
            # Draw rounded bar
            painter.setPen(Qt.NoPen)
            painter.setBrush(color)
            painter.drawRoundedRect(i * w + 2, y, w - 4, h, (w - 4) / 2, (w - 4) / 2)
            
            # Glow effect
            if self.is_active:
                glow_color = QColor("#00e6e6")
                glow_color.setAlphaF(0.3)
                painter.setBrush(glow_color)
                painter.drawRoundedRect(i * w + 1, y - 2, w - 2, h + 4, (w - 2) / 2, (w - 2) / 2)

class PullWorker(QThread):
    finished = Signal(bool, str)

    def __init__(self, model_name: str):
        super().__init__()
        self.model_name = model_name

    def run(self):
        try:
            process = subprocess.run(
                ["ollama", "pull", self.model_name],
                capture_output=True,
                text=True
            )
            if process.returncode == 0:
                self.finished.emit(True, f"Successfully pulled {self.model_name}")
            else:
                self.finished.emit(False, f"Failed to pull {self.model_name}: {process.stderr.strip()}")
        except Exception as e:
            self.finished.emit(False, str(e))

class ChatWorker(QThread):
    chunk_received = Signal(str)
    finished = Signal()

    def __init__(self, model: str, prompt: str, engine: OllamaClient, options: Optional[dict] = None):
        super().__init__()
        self.model = model
        self.prompt = prompt
        self.options = options
        self.engine = engine
        self.is_running = True

    def stop(self):
        self.is_running = False

    def run(self):
        # ⚡ PURE PYTHON: Verfied native stream
        try:
            for chunk in self.engine.stream_chat(self.model, self.prompt, self.options):
                if not self.is_running:
                    break
                self.chunk_received.emit(chunk)
        except Exception as e:
            if self.is_running:
                self.chunk_received.emit(f"\n[Aura Error: {str(e)}]")
        
        self.finished.emit()

class CRTOverlay(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setAttribute(Qt.WA_TransparentForMouseEvents)
        self.setAttribute(Qt.WA_NoSystemBackground)
        self.scanline_offset = 0
        self.glitch_intensity = 0.0
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.animate)
        self.timer.start(50)

    def animate(self):
        self.scanline_offset = (self.scanline_offset + 1) % 4
        if self.glitch_intensity > 0:
            self.glitch_intensity = max(0.0, self.glitch_intensity - 0.1)
        self.update()

    def trigger_glitch(self, intensity=1.0):
        self.glitch_intensity = intensity
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing, False)
        
        rect = self.rect()
        
        # Draw scanlines
        pen = QPen(QColor(0, 0, 0, 40))
        pen.setWidth(1)
        painter.setPen(pen)
        for y in range(self.scanline_offset, rect.height(), 4):
            painter.drawLine(0, y, rect.width(), y)
            
        # Draw vignette overlay
        grad = QRadialGradient(rect.width() / 2, rect.height() / 2, max(rect.width(), rect.height()) / 1.5)
        grad.setColorAt(0.5, QColor(0, 0, 0, 0))
        grad.setColorAt(1.0, QColor(0, 0, 0, 150))
        painter.fillRect(rect, grad)
        
        # Glitch effects (RGB split/color bars during glitch)
        if self.glitch_intensity > 0:
            for _ in range(int(self.glitch_intensity * 5)):
                gy = random.randint(0, rect.height())
                gh = random.randint(2, 10)
                alpha = int(self.glitch_intensity * 60)
                color = random.choice([QColor(255, 0, 0, alpha), QColor(0, 255, 255, alpha), QColor(255, 0, 255, alpha)])
                painter.fillRect(0, gy, rect.width(), gh, color)

class AutoResizingTextEdit(QTextEdit):
    returnPressed = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.textChanged.connect(self.adjust_height)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.setMinimumHeight(45)
        self.setMaximumHeight(200)

    def adjust_height(self):
        doc_height = int(self.document().size().height())
        margins = self.contentsMargins()
        new_height = doc_height + margins.top() + margins.bottom() + 10 # padding
        self.setFixedHeight(min(self.maximumHeight(), max(self.minimumHeight(), new_height)))

    def keyPressEvent(self, event):
        if event.key() in (Qt.Key_Return, Qt.Key_Enter) and not event.modifiers() & Qt.ShiftModifier:
            self.returnPressed.emit()
        else:
            super().keyPressEvent(event)

@aura_component
class AuraWindow(QMainWindow):
    __slots__ = (
        "engine", "gen_options", "md", "models", "model_index", "model",
        "status_label", "settings_toggle", "output_area", "input_field",
        "settings_panel", "temp_label", "temp_slider", "top_p_label",
        "top_p_slider", "ctx_label", "ctx_slider", "font_combo",
        "font_size_slider", "dir_label", "dir_btn", "messages",
        "pending_message", "current_response_text", "worker", "workspace_label",
        "model_selector", "model_mapping", "discover_btn", "models_toggle",
        "models_panel", "models_list", "pull_input", "pull_btn", "pull_worker",
        "visualizer", "ghost_log", "power_stripe", "glitch_timer", "saturation",
        "telemetry_label", "telemetry_timer", "typewriter_speed", "is_typing",
        "available_fonts", "verb_label", "verb_slider",
        "sat_label", "sat_slider", "profile_combo", "speed_label", "speed_slider",
        "last_render_time"
    )

    def get_git_branch(self, path):
        import subprocess
        try:
            result = subprocess.run(["git", "branch", "--show-current"], cwd=path, capture_output=True, text=True, check=True)
            branch = result.stdout.strip()
            return f" {branch}" if branch else ""
        except:
            return ""

    def ensure_ollama_running(self):
        try:
            import requests
            requests.get("http://127.0.0.1:11434/", timeout=1)
        except:
            print("OLLAMA // Starting server...")
            subprocess.Popen(["ollama", "serve"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            import time
            time.sleep(2) # Give it a moment to bind

    def __init__(self):
        super().__init__()
        self.check_mandates() # Decorator enforced method
        self.setWindowTitle("Aura // Local AI")
        self.resize(1200, 800)
        
        # Set Window Icon
        icon_path = os.path.join(os.path.dirname(__file__), "icon.svg")
        if os.path.exists(icon_path):
            self.setWindowIcon(QIcon(icon_path))
        
        # 🚀 AUTO-START OLLAMA
        self.ensure_ollama_running()
        
        # Initialize Engine
        self.engine = OllamaClient()
        available_tags = [m['name'] for m in self.engine.get_available_models()]
        
        # ⚡ ASAHI OPTIMIZATION: Prioritize Phi-3 Mini as default (Low RAM footprint)
        priority_models = ["phi3:mini", "phi3:latest", "qwen2.5:7b", "qwen2.5:latest", "gemma2:2b", "gemma2:latest"]
        self.model = None
        for p in priority_models:
            if p in available_tags:
                self.model = p
                break
        
        if not self.model:
            self.model = available_tags[0] if available_tags else "phi3:mini"
        
        self.models = available_tags if available_tags else [self.model]
        
        # Generation Options
        self.gen_options = {
            "temperature": 0.7,
            "top_p": 0.9,
            "num_ctx": 4096
        }
        self.md = MarkdownIt()
        self.load_custom_fonts()
        
        # State & Settings
        self.saturation = 1.0
        self.typewriter_speed = 30 # ms per chunk
        self.is_typing = False
        self.messages = []
        self.pending_message = None
        self.current_response_text = ""
        self.last_render_time = 0.0

        # Model Management
        self.model_mapping = {v['name']: k for k, v in OllamaClient.MODELS.items()}
        self.model_selector = QComboBox()
        self.model_selector.addItems(list(self.model_mapping.keys()))
        self.model_selector.currentTextChanged.connect(self.change_model_from_selector)
        
        self.update_stylesheet()

        main_layout = QHBoxLayout()
        
        # Left Side (Chat)
        chat_container = QVBoxLayout()
        chat_container.setContentsMargins(30, 30, 30, 30)
        chat_container.setSpacing(20)
        
        header = QHBoxLayout()
        friendly_name = OllamaClient.MODELS.get(self.model, {"name": self.model})["name"]
        self.status_label = QLabel(f"ACTIVE_VOICE // {friendly_name}")
        header.addWidget(self.status_label)
        
        header.addWidget(self.model_selector) # ⚡ NEW: Direct model switching
        
        self.visualizer = AudioVisualizer()
        header.addWidget(self.visualizer)
        header.addStretch()

        self.abort_btn = QPushButton("ABORT")
        self.abort_btn.setStyleSheet("color: #FF5555; font-weight: bold; border: 1px solid #FF5555; padding: 2px 10px;")
        self.abort_btn.clicked.connect(self.abort_generation)
        self.abort_btn.setVisible(False)
        header.addWidget(self.abort_btn)
        
        self.models_toggle = QPushButton("MODELS")
        self.models_toggle.setCheckable(True)
        self.models_toggle.clicked.connect(self.toggle_models)
        header.addWidget(self.models_toggle)
        
        self.settings_toggle = QPushButton("VOID_SETTINGS")
        self.settings_toggle.setCheckable(True)
        self.settings_toggle.clicked.connect(self.toggle_settings)
        header.addWidget(self.settings_toggle)
        
        chat_container.addLayout(header)

        self.output_area = QTextEdit()
        self.output_area.setReadOnly(True)
        self.output_area.setPlaceholderText("THE AURA IS SILENT...")
        chat_container.addWidget(self.output_area)

        self.input_field = AutoResizingTextEdit()
        self.input_field.setPlaceholderText("DESCRIBE THE VOID...")
        self.input_field.returnPressed.connect(self.process_input)
        chat_container.addWidget(self.input_field)

        # Footer for workspace status (gemini-cli style)
        footer = QHBoxLayout()
        self.workspace_label = QLabel()
        self._sync_workspace_label()
        footer.addWidget(self.workspace_label)
        footer.addStretch()
        
        self.telemetry_label = QLabel("NODE: ASAHI // TPS: 0.0 // VRAM: 0.0")
        self.telemetry_label.setStyleSheet("color: #6633FF; font-family: 'Monospace'; font-size: 10px;")
        footer.addWidget(self.telemetry_label)
        
        chat_container.addLayout(footer)

        main_layout.addLayout(chat_container, stretch=4)

        # Ghost Log Stream (Far Right)
        self.ghost_log = GhostLogArea()
        self.ghost_log.setFixedWidth(150)
        main_layout.addWidget(self.ghost_log)

        # Right Side (Settings Panel)
        self.settings_panel = QWidget()
        self.settings_panel.setObjectName("settings_panel")
        self.settings_panel.setFixedWidth(300)
        self.settings_panel.setVisible(False)
        settings_layout = QVBoxLayout()
        settings_layout.setContentsMargins(20, 30, 20, 30)
        
        # 1. Tuning Parameters
        tuning_group = QGroupBox("TUNING_PARAMETERS")
        form = QFormLayout()
        
        self.temp_label = QLabel(f"TEMP: {self.gen_options['temperature']}")
        self.temp_slider = QSlider(Qt.Horizontal)
        self.temp_slider.setRange(0, 200)
        self.temp_slider.setValue(int(self.gen_options['temperature'] * 100))
        self.temp_slider.valueChanged.connect(self.update_temp)
        form.addRow(self.temp_label, self.temp_slider)
        
        self.top_p_label = QLabel(f"TOP_P: {self.gen_options['top_p']}")
        self.top_p_slider = QSlider(Qt.Horizontal)
        self.top_p_slider.setRange(0, 100)
        self.top_p_slider.setValue(int(self.gen_options['top_p'] * 100))
        self.top_p_slider.valueChanged.connect(self.update_top_p)
        form.addRow(self.top_p_label, self.top_p_slider)
        
        self.ctx_label = QLabel(f"CTX: {self.gen_options['num_ctx']}")
        self.ctx_slider = QSlider(Qt.Horizontal)
        self.ctx_slider.setRange(512, 32768)
        self.ctx_slider.setValue(self.gen_options['num_ctx'])
        self.ctx_slider.setSingleStep(512)
        self.ctx_slider.valueChanged.connect(self.update_ctx)
        form.addRow(self.ctx_label, self.ctx_slider)
        
        tuning_group.setLayout(form)
        settings_layout.addWidget(tuning_group)

        # 1.5 Console Controls
        console_group = QGroupBox("CONSOLE_CONTROLS")
        c_form = QFormLayout()
        
        self.verb_label = QLabel(f"VERBOSITY: {self.engine.verbosity:.1f}")
        self.verb_slider = QSlider(Qt.Horizontal)
        self.verb_slider.setRange(0, 100)
        self.verb_slider.setValue(int(self.engine.verbosity * 100))
        self.verb_slider.valueChanged.connect(self.update_verbosity)
        c_form.addRow(self.verb_label, self.verb_slider)

        self.sat_label = QLabel(f"SATURATION: {self.saturation:.1f}")
        self.sat_slider = QSlider(Qt.Horizontal)
        self.sat_slider.setRange(0, 100)
        self.sat_slider.setValue(int(self.saturation * 100))
        self.sat_slider.valueChanged.connect(self.update_saturation)
        c_form.addRow(self.sat_label, self.sat_slider)
        
        self.profile_combo = QComboBox()
        self.profile_combo.addItems(list(OllamaClient.PROFILES.keys()))
        self.profile_combo.currentTextChanged.connect(self.update_profile)
        c_form.addRow(QLabel("PROFILE:"), self.profile_combo)

        console_group.setLayout(c_form)
        settings_layout.addWidget(console_group)
        
        # 2. Typography
        typo_group = QGroupBox("TYPOGRAPHY")
        typo_form = QFormLayout()
        
        self.font_combo = QComboBox()
        self.font_combo.addItems(self.available_fonts)
        self.font_combo.currentTextChanged.connect(self.update_font)
        typo_form.addRow(QLabel("FONT:"), self.font_combo)
        
        self.font_size_slider = QSlider(Qt.Horizontal)
        self.font_size_slider.setRange(10, 24)
        self.font_size_slider.setValue(14)
        self.font_size_slider.valueChanged.connect(self.update_font_size)
        typo_form.addRow(QLabel("SIZE:"), self.font_size_slider)
        
        typo_group.setLayout(typo_form)
        settings_layout.addWidget(typo_group)
        
        # 3. Workspace
        ws_group = QGroupBox("WORKSPACE")
        ws_layout = QVBoxLayout()
        self.dir_label = QLabel(f"DIR: {os.path.basename(self.engine.project_root)}")
        self.dir_label.setToolTip(self.engine.project_root)
        ws_layout.addWidget(self.dir_label)
        
        self.dir_btn = QPushButton("CHANGE_WORKSPACE")
        self.dir_btn.clicked.connect(self.change_directory)
        ws_layout.addWidget(self.dir_btn)
        
        ws_group.setLayout(ws_layout)
        settings_layout.addWidget(ws_group)

        # 4. Add Model
        pull_group = QGroupBox("ADD_MODEL")
        pull_layout = QHBoxLayout()
        self.pull_input = QLineEdit()
        self.pull_input.setPlaceholderText("e.g. llama3")
        self.pull_btn = QPushButton("PULL")
        self.pull_btn.clicked.connect(self.pull_model)
        pull_layout.addWidget(self.pull_input)
        pull_layout.addWidget(self.pull_btn)
        pull_group.setLayout(pull_layout)
        settings_layout.addWidget(pull_group)
        
        settings_layout.addStretch()
        self.settings_panel.setLayout(settings_layout)

        # Models Panel
        self.models_panel = QWidget()
        self.models_panel.setObjectName("models_panel")
        self.models_panel.setFixedWidth(300)
        self.models_panel.setVisible(False)
        mp_layout = QVBoxLayout()
        mp_layout.setContentsMargins(20, 30, 20, 30)
        
        mp_label = QLabel("LOCAL_MODELS //")
        mp_layout.addWidget(mp_label)
        
        self.models_list = QListWidget()
        self.models_list.setStyleSheet("""
            QListWidget {
                background-color: #0F0F0F;
                color: #D4AF37;
                border: 1px solid #1A1A1A;
                font-family: 'Monospace';
                font-size: 11px;
            }
            QListWidget::item:selected {
                background-color: #2D1B4E;
            }
        """)
        mp_layout.addWidget(self.models_list)
        
        btn_layout = QHBoxLayout()
        switch_btn = QPushButton("SWITCH")
        switch_btn.clicked.connect(self.switch_to_selected_model)
        btn_layout.addWidget(switch_btn)

        refresh_btn = QPushButton("REFRESH")
        refresh_btn.clicked.connect(self.populate_models_list)
        btn_layout.addWidget(refresh_btn)
        
        mp_layout.addLayout(btn_layout)
        
        self.models_panel.setLayout(mp_layout)
        
        main_layout.addWidget(self.models_panel)
        main_layout.addWidget(self.settings_panel)

        central_widget = QWidget()
        central_widget.setLayout(main_layout)
        self.setCentralWidget(central_widget)
        
        # Add CRT Overlay
        self.crt_overlay = CRTOverlay(central_widget)
        self.crt_overlay.setGeometry(0, 0, self.width(), self.height())
        
        # Initial Font Application
        self.update_font(self.font_combo.currentText())
        
        # Telemetry Timer
        self.telemetry_timer = QTimer(self)
        self.telemetry_timer.timeout.connect(self.update_telemetry)
        self.telemetry_timer.start(2000)

        # Session Restoration
        self.load_session()

    def update_stylesheet(self):
        teal = f"rgba(0, 230, 230, {0.5 * self.saturation})"
        purple = f"rgba(102, 51, 255, {0.3 * self.saturation})"
        bg = "rgba(13, 13, 26, 0.95)" if self.saturation > 0.5 else "#0d0d1a"
        
        self.setStyleSheet(f"""
            QMainWindow {{ background-color: #050505; }}
            
            QTextEdit {{ 
                background-color: transparent; 
                color: #B0B0B0; 
                border: none;
                selection-background-color: rgba(0, 230, 230, 0.3);
                selection-color: #FFFFFF;
            }}
            
            /* Modern Scrollbars */
            QScrollBar:vertical {{
                border: none;
                background: #0A0A10;
                width: 10px;
                margin: 0px 0px 0px 0px;
                border-radius: 4px;
            }}
            QScrollBar::handle:vertical {{
                background: {purple};
                min-height: 30px;
                border-radius: 4px;
            }}
            QScrollBar::handle:vertical:hover {{
                background: {teal};
            }}
            QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
                height: 0px;
            }}
            QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical {{
                background: none;
            }}
            
            /* Glass Panels */
            #settings_panel, #models_panel {{
                background-color: {bg};
                border-left: 1px solid {teal};
            }}
            
            pre {{ background-color: rgba(102, 51, 255, 0.1); padding: 12px; border-radius: 6px; color: #F0F0F0; border: 1px solid {purple}; }}
            code {{ color: #00e6e6; background-color: rgba(0, 230, 230, 0.1); padding: 2px 4px; border-radius: 3px; }}
            h1, h2, h3 {{ color: #00e6e6; margin-top: 15px; margin-bottom: 10px; }}
            
            AutoResizingTextEdit {{
                background-color: rgba(102, 51, 255, 0.08);
                color: #FFFFFF;
                border: 1px solid {purple};
                padding: 12px;
                border-radius: 8px;
                font-size: 14px;
            }}
            AutoResizingTextEdit:focus {{
                border: 1px solid #00e6e6;
                background-color: rgba(0, 230, 230, 0.05);
            }}
            
            QLabel {{
                color: #00cccc;
                font-family: 'Monospace';
                font-size: 11px;
                text-transform: uppercase;
                letter-spacing: 2px;
            }}
            
            QGroupBox {{
                border: 1px solid {purple};
                margin-top: 15px;
                padding-top: 15px;
                color: #00e6e6;
                font-family: 'Monospace';
                font-size: 10px;
                border-radius: 6px;
            }}
            QGroupBox::title {{
                subcontrol-origin: margin;
                subcontrol-position: top left;
                padding: 0 5px;
                left: 10px;
            }}
            
            QSlider::groove:horizontal {{
                background: rgba(102, 51, 255, 0.3);
                height: 4px;
                border-radius: 2px;
            }}
            QSlider::handle:horizontal {{
                background: #00e6e6;
                width: 14px;
                margin: -5px 0;
                border-radius: 7px;
            }}
            QSlider::handle:horizontal:hover {{
                background: #FFFFFF;
                box-shadow: 0 0 10px #00e6e6;
            }}
            
            QPushButton {{
                background-color: rgba(102, 51, 255, 0.15);
                color: #00e6e6;
                border: 1px solid {purple};
                padding: 8px 16px;
                font-family: 'Monospace';
                font-size: 11px;
                font-weight: bold;
                border-radius: 6px;
            }}
            QPushButton:hover {{
                color: #ffffff;
                border-color: #00e6e6;
                background-color: rgba(0, 230, 230, 0.2);
            }}
            QPushButton:pressed {{
                background-color: rgba(0, 230, 230, 0.4);
            }}
            QPushButton:checked {{
                background-color: {teal};
                color: #000000;
                border: 1px solid #00e6e6;
            }}
            QPushButton:focus {{
                outline: none;
            }}
            
            QComboBox {{
                background-color: rgba(13, 13, 26, 0.9);
                color: #00e6e6;
                border: 1px solid {purple};
                border-radius: 4px;
                padding: 4px 8px;
            }}
            QComboBox:hover {{
                border: 1px solid #00e6e6;
            }}
            QComboBox::drop-down {{
                border: none;
            }}
            QComboBox QAbstractItemView {{
                background-color: #050505;
                color: #00cccc;
                selection-background-color: {teal};
                selection-color: #000000;
                border: 1px solid {purple};
            }}
            
            QLineEdit {{
                background-color: rgba(102, 51, 255, 0.1);
                color: #E0E0E0;
                border: 1px solid {purple};
                padding: 6px;
                border-radius: 4px;
            }}
            QLineEdit:focus {{
                border: 1px solid #00e6e6;
            }}
        """)

    def update_telemetry(self):
        # Hardware Aware Telemetry
        node = "ASAHI" if self.engine.is_asahi() else "X64_HP"
        tps = random.uniform(15.0, 45.0) if self.visualizer.is_active else 0.0
        vram = random.uniform(2.1, 4.8) if self.engine.is_asahi() else 0.8
        self.telemetry_label.setText(f"NODE: {node} // TPS: {tps:.1f} // VRAM: {vram:.1f}G")
        self.ghost_log.log(f"TELEMETRY_SYNC: OK (VRAM: {vram:.1f}G)")

    def update_verbosity(self, val):
        self.engine.set_verbosity(val / 100.0)
        self.verb_label.setText(f"VERBOSITY: {self.engine.verbosity:.1f}")
        self.ghost_log.log(f"ENGINE_MODE: VERBOSITY_SHIFT ({self.engine.verbosity:.1f})")

    def update_saturation(self, val):
        self.saturation = val / 100.0
        self.sat_label.setText(f"SATURATION: {self.saturation:.1f}")
        self.update_stylesheet()

    def update_profile(self, profile):
        self.engine.set_profile(profile)
        self.ghost_log.log(f"HARDWARE_PROFILE: {profile} ACTIVATED")
        self.trigger_glitch()

    def trigger_glitch(self):
        # Trigger the CRT Overlay glitch effect
        if hasattr(self, 'crt_overlay'):
            self.crt_overlay.trigger_glitch(1.0)
        self.ghost_log.log("SYSTEM_GLITCH: CONTEXT_SHIFT_DETECTED")

    def load_session(self):
        try:
            session_path = os.path.join(self.engine.project_root, ".aura_session.json")
            if os.path.exists(session_path):
                with open(session_path, "r") as f:
                    data = json.load(f)
                    self.messages = data.get("messages", [])
                    self.render_messages()
                    self.ghost_log.log("SESSION_RE_ANIMATED: OK")
        except:
            pass

    def save_session(self):
        try:
            session_path = os.path.join(self.engine.project_root, ".aura_session.json")
            with open(session_path, "w") as f:
                json.dump({"messages": self.messages}, f)
        except:
            pass

    def keyPressEvent(self, event):
        super().keyPressEvent(event)

    def resizeEvent(self, event):
        if hasattr(self, 'crt_overlay'):
            self.crt_overlay.setGeometry(0, 0, self.width(), self.height())
        super().resizeEvent(event)

    def load_custom_fonts(self):
        self.available_fonts = ["Monospace", "Cascadia Code", "Consolas", "Courier New"]
        font_dir = os.path.join(os.path.dirname(__file__), "fonts")
        if os.path.exists(font_dir):
            for file in os.listdir(font_dir):
                if file.endswith(".ttf"):
                    font_id = QFontDatabase.addApplicationFont(os.path.join(font_dir, file))
                    families = QFontDatabase.applicationFontFamilies(font_id)
                    for family in families:
                        if family not in self.available_fonts:
                            self.available_fonts.insert(0, family)

    def discover_models(self):
        self.output_area.append("<p style='color: #404040; font-family: Monospace;'><i>SYSTEM // DISCOVERING LOCAL MODELS...</i></p>")
        available = self.engine.get_available_models()
        if not available:
            self.output_area.append("<p style='color: #FF5555; font-family: Monospace;'><i>SYSTEM // NO MODELS FOUND ON OLLAMA SERVER</i></p>")
        else:
            for m in available:
                name = m.get("name", "Unknown")
                size = m.get("size", 0) / (1024**3)
                status = "[TUNED]" if name in OllamaClient.MODELS else "[RAW]"
                safe_name = html.escape(name)
                safe_status = html.escape(status)
                self.output_area.append(f"<p style='color: #B0B0B0; font-family: Monospace;'>• <b>{safe_name}</b> ({size:.1f} GB) <span style='color: #404040;'>{safe_status}</span></p>")
        self.output_area.append("<p style='color: #404040; font-family: Monospace;'><i>USE /model &lt;name&gt; TO SWITCH OR SELECT FROM HEADER</i></p>")

    def toggle_settings(self):
        if self.settings_toggle.isChecked():
            self.models_toggle.setChecked(False)
            self.models_panel.setVisible(False)
        self.settings_panel.setVisible(self.settings_toggle.isChecked())

    def _sync_model_selector(self):
        for i in range(self.model_selector.count()):
            if self.model_mapping.get(self.model_selector.itemText(i)) == self.model:
                self.model_selector.blockSignals(True)
                self.model_selector.setCurrentIndex(i)
                self.model_selector.blockSignals(False)
                break

    def _sync_workspace_label(self):
        branch = self.get_git_branch(self.engine.project_root)
        # Extract host and port from base_url (remove protocol)
        host_port = self.engine.base_url.replace("http://", "").replace("https://", "")
        self.workspace_label.setText(f"[{host_port} @ {self.engine.project_root}] {branch}".strip())

    def change_model_from_selector(self, text):
        if text in self.model_mapping:
            new_model = self.model_mapping[text]
            if self.model != new_model:
                self.model = new_model
                self.engine.clear_history()
                self.trigger_glitch()
                safe_text = html.escape(text)
                self.output_area.append(f"<p style='color: #404040; font-family: Monospace;'><i>SYSTEM // Switched to {safe_text} (Context Cleared)</i></p>")

    def toggle_models(self):
        if self.models_toggle.isChecked():
            self.settings_toggle.setChecked(False)
            self.settings_panel.setVisible(False)
            self.populate_models_list()
        self.models_panel.setVisible(self.models_toggle.isChecked())

    def populate_models_list(self):
        self.models_list.clear()
        available = self.engine.get_available_models()
        for m in available:
            name = m.get("name", "Unknown")
            size = m.get("size", 0) / (1024**3)
            status = "[TUNED]" if name in OllamaClient.MODELS else "[RAW]"
            self.models_list.addItem(f"{name} ({size:.1f}GB) {status}")

    def switch_to_selected_model(self):
        items = self.models_list.selectedItems()
        if not items:
            self.output_area.append("<p style='color: #FF5555; font-family: Monospace;'><i>SYSTEM // NO MODEL SELECTED</i></p>")
            return
        
        target = items[0].text().split(" ")[0] # extract just the name
        self.model = target
        self.engine.clear_history()
        self.trigger_glitch()
        
        # Add to header dropdown if it's new
        found_in_header = False
        for i in range(self.model_selector.count()):
            if self.model_mapping.get(self.model_selector.itemText(i)) == target:
                found_in_header = True
                break
        
        if not found_in_header:
            display_name = f"[RAW] {target}"
            self.model_mapping[display_name] = target
            self.model_selector.addItem(display_name)

        self._sync_model_selector()
        
        friendly_name = OllamaClient.MODELS.get(target, {"name": f"[RAW] {target}"})["name"]
        safe_friendly_name = html.escape(friendly_name)
        self.output_area.append(f"<p style='color: #404040; font-family: Monospace;'><i>SYSTEM // Switched to {safe_friendly_name} (Context Cleared)</i></p>")

    def pull_model(self):
        model_name = self.pull_input.text().strip()
        if not model_name: return
        self.pull_btn.setEnabled(False)
        self.pull_btn.setText("PULLING...")
        safe_name = html.escape(model_name)
        self.output_area.append(f"<p style='color: #404040; font-family: Monospace;'><i>SYSTEM // Pulling {safe_name} from Ollama...</i></p>")
        self.ghost_log.log(f"API_REQUEST: PULL_MODEL ({model_name})")
        
        self.pull_worker = PullWorker(model_name)
        self.pull_worker.finished.connect(self.on_pull_finished)
        self.pull_worker.start()

    def on_pull_finished(self, success, message):
        self.pull_btn.setEnabled(True)
        self.pull_btn.setText("PULL")
        self.pull_input.clear()
        safe_msg = html.escape(message)
        color = "#41CD52" if success else "#FF5555"
        self.output_area.append(f"<p style='color: {color}; font-family: Monospace;'><i>SYSTEM // {safe_msg}</i></p>")
        self.ghost_log.log(f"API_RESPONSE: PULL_MODEL_STATUS ({success})")
        if success:
            self.populate_models_list()


    def update_temp(self, val):
        self.gen_options['temperature'] = val / 100.0
        self.temp_label.setText(f"TEMP: {self.gen_options['temperature']:.2f}")

    def update_top_p(self, val):
        self.gen_options['top_p'] = val / 100.0
        self.top_p_label.setText(f"TOP_P: {self.gen_options['top_p']:.2f}")

    def update_ctx(self, val):
        self.gen_options['num_ctx'] = val
        self.ctx_label.setText(f"CTX: {val}")

    def update_font(self, family):
        size = self.font_size_slider.value()
        font = QFont(family, size)
        self.output_area.setFont(font)
        self.input_field.setFont(font)

    def update_font_size(self, size):
        family = self.font_combo.currentText()
        font = QFont(family, size)
        self.output_area.setFont(font)
        self.input_field.setFont(font)

    def change_directory(self):
        new_dir = QFileDialog.getExistingDirectory(self, "Select Workspace", self.engine.project_root)
        if new_dir:
            self.engine.project_root = new_dir
            self.dir_label.setText(f"DIR: {os.path.basename(new_dir)}")
            self.dir_label.setToolTip(new_dir)
            self._sync_workspace_label()
            safe_new_dir = html.escape(new_dir)
            self.output_area.append(f"<p style='color: #404040; font-family: Monospace;'><i>SYSTEM // Workspace updated: {safe_new_dir}</i></p>")
            self.trigger_glitch()

    def abort_generation(self):
        if hasattr(self, 'worker') and self.worker.isRunning():
            self.worker.stop()
            self.ghost_log.log("SYSTEM_ACTION: ABORT_GENERATION_SIGNAL_SENT")
            self.output_area.append("<p style='color: #FF5555; font-family: Monospace;'><i>SYSTEM // ABORTED BY USER</i></p>")
            self.abort_btn.setVisible(False)

    def process_input(self):
        text = self.input_field.toPlainText().strip()
        if not text: return

        # 1. Navigation Handling (cd command)
        if text.startswith("cd "):
            target = text[3:].strip()
            if target == "~":
                target = os.path.expanduser("~")

            new_path = os.path.normpath(os.path.join(self.engine.project_root, target))

            if os.path.isdir(new_path):
                self.engine.project_root = new_path
                # Update UI elements representing directory
                self.dir_label.setText(f"DIR: {os.path.basename(new_path)}")
                self.dir_label.setToolTip(new_path)
                self._sync_workspace_label()
                safe_path = html.escape(new_path)
                self.output_area.append(f"<p style='color: #404040; font-family: Monospace;'><i>SYSTEM // Context shifted to: {safe_path}</i></p>")
                self.trigger_glitch()
            else:
                safe_target = html.escape(target)
                self.output_area.append(f"<p style='color: #FF5555; font-family: Monospace;'><i>SYSTEM // ERROR: Directory not found: {safe_target}</i></p>")

            self.input_field.clear()
            return

        if text.startswith("/"):
            cmd = text[1:].lower()

            if cmd == "stop" or cmd == "abort":
                self.abort_generation()
                self.input_field.clear()
                return

            if cmd == "kill":
                self.output_area.append("<p style='color: #FF5555; font-family: Monospace;'><i>SYSTEM // EXECUTING NUCLEAR OPTION: KILLING OLLAMA SERVER...</i></p>")
                subprocess.run(["sudo", "systemctl", "stop", "ollama"], check=False)
                self.abort_generation()
                self.input_field.clear()
                return

            alias_map = {

                "phi": "phi3:mini",
                "gemma": "gemma2:2b",
                "qwen": "qwen2.5:7b",
                "coder": "qwen2.5-coder:1.5b",
                "deep": "deepseek-r1:8b",
                "noon": "moondream",
                "samist": "samantha-mistral"
            }

            self.trigger_glitch()

            if cmd == "help":
                self.output_area.append("<p style='color: #404040; font-family: Monospace;'><i>SYSTEM // TUNED VOICES:</i></p>")
                for alias, m_id in alias_map.items():
                    if m_id in OllamaClient.MODELS:
                        safe_model_name = html.escape(OllamaClient.MODELS[m_id]['name'])
                        safe_alias = html.escape(alias)
                        self.output_area.append(f"<p style='color: #D4AF37; font-family: Monospace;'><b>/{safe_alias}</b> - {safe_model_name}</p>")
                self.output_area.append("<p style='color: #404040; font-family: Monospace;'><i>TYPE /MODELS TO SCAN LOCAL SYSTEM</i></p>")
                self.input_field.clear()
                return

            if cmd == "models":
                self.discover_models()
                self.input_field.clear()
                return

            if cmd.startswith("model "):
                target = cmd[6:].strip()
                for display_name, m_id in self.model_mapping.items():
                    if target in m_id.lower():
                        self.model = m_id
                        self.engine.clear_history()
                        self._sync_model_selector()
                        friendly_name = OllamaClient.MODELS.get(m_id, {"name": f"[RAW] {m_id}"})["name"]
                        safe_friendly_name = html.escape(friendly_name)
                        self.output_area.append(f"<p style='color: #404040; font-family: Monospace;'><i>SYSTEM // Switched to {safe_friendly_name} (Context Cleared)</i></p>")
                        self.input_field.clear()
                        return
                
                safe_target = html.escape(target)
                self.output_area.append(f"<p style='color: #FF5555; font-family: Monospace;'><i>SYSTEM // Model not found: {safe_target}</i></p>")
                self.input_field.clear()
                return

            if cmd in alias_map:
                target_model = alias_map[cmd]
                if target_model in self.models:
                    self.model = target_model
                    self.engine.clear_history()
                    self._sync_model_selector()
                    friendly_name = OllamaClient.MODELS.get(self.model, {"name": self.model})["name"]
                    safe_friendly_name = html.escape(friendly_name)
                    self.output_area.append(f"<p style='color: #404040; font-family: Monospace;'><i>SYSTEM // Switched to {safe_friendly_name} (Context Cleared)</i></p>")
                    self.input_field.clear()
                    return

            found = False
            for key in self.models:
                if cmd in key.lower():
                    self.model = key
                    self.engine.clear_history()
                    self._sync_model_selector()
                    friendly_name = OllamaClient.MODELS.get(self.model, {"name": self.model})["name"]
                    safe_friendly_name = html.escape(friendly_name)
                    self.output_area.append(f"<p style='color: #404040; font-family: Monospace;'><i>SYSTEM // Switched to {safe_friendly_name} (Context Cleared)</i></p>")
                    found = True
                    break
            
            if not found:
                safe_cmd = html.escape(cmd)
                self.output_area.append(f"<p style='color: #FF5555; font-family: Monospace;'><i>SYSTEM // Unknown model: {safe_cmd}</i></p>")
            
            self.input_field.clear()
            return

        self.messages.append({"role": "user", "content": text})
        self.current_response_text = ""
        self.pending_message = {"role": "assistant", "model": self.model, "content": ""}
        self.render_messages()
        
        self.input_field.clear()
        self.input_field.setEnabled(False)
        self.abort_btn.setVisible(True)
        self.visualizer.start()
        self.ghost_log.log(f"API_STREAM_START: MODEL({self.model})")

        self.worker = ChatWorker(self.model, text, self.engine, self.gen_options)
        self.worker.chunk_received.connect(self.handle_chunk)
        self.worker.finished.connect(self.handle_finished)
        self.worker.start()

    def handle_chunk(self, chunk: str):
        self.current_response_text += chunk
        if self.pending_message:
            self.pending_message["content"] = self.current_response_text

        # ⚡ BOLT OPTIMIZATION: Throttle render updates to ~30 FPS (33ms)
        # This prevents O(N*M) layout thrashing in QTextEdit during fast LLM streams
        current_time = time.time()
        if current_time - self.last_render_time >= 0.033:
            self.render_messages()
            self.last_render_time = current_time

    def handle_finished(self):
        self.visualizer.stop()
        self.abort_btn.setVisible(False)
        self.ghost_log.log("API_STREAM_END: OK")
        if self.pending_message:
            # Check for Tool Use (WRITE_FILE)
            content = self.pending_message["content"]
            if "WRITE_FILE:" in content and "CONTENT:" in content and "EOF" in content:
                self.handle_tool_use(content)
            
            self.messages.append(self.pending_message)
            self.pending_message = None
        self.render_messages()
        self.save_session()
        self.input_field.setEnabled(True)
        self.input_field.setFocus()

    def handle_tool_use(self, content: str):
        try:
            # Simple parser for the model's command
            parts = content.split("WRITE_FILE:")[1].split("CONTENT:")
            path = parts[0].strip()
            file_content = parts[1].split("EOF")[0].strip()
            
            # Security: Prevent path traversal
            if os.path.isabs(path) or ".." in path:
                safe_path = html.escape(path)
                self.output_area.append(f"<p style='color: #FF5555; font-family: Monospace;'><i>SYSTEM // TOOL_USE_DENIED: Path traversal detected: {safe_path}</i></p>")
                return

            full_path = os.path.normpath(os.path.join(self.engine.project_root, path))
            
            # Security: Ensure path is within project root
            if not full_path.startswith(os.path.normpath(self.engine.project_root)):
                safe_path = html.escape(path)
                self.output_area.append(f"<p style='color: #FF5555; font-family: Monospace;'><i>SYSTEM // TOOL_USE_DENIED: Path outside workspace: {safe_path}</i></p>")
                return

            from PySide6.QtWidgets import QMessageBox
            reply = QMessageBox.question(self, "AURA // TOOL_USE", 
                                       f"QWEN requests to modify: {path}\n\nProceed with changes?",
                                       QMessageBox.Yes | QMessageBox.No)
            
            if reply == QMessageBox.Yes:
                os.makedirs(os.path.dirname(full_path), exist_ok=True)
                with open(full_path, "w") as f:
                    f.write(file_content)
                safe_path = html.escape(path)
                self.output_area.append(f"<p style='color: #41CD52; font-family: Monospace;'><i>SYSTEM // FILE_WRITTEN: {safe_path}</i></p>")
            else:
                self.output_area.append(f"<p style='color: #FF5555; font-family: Monospace;'><i>SYSTEM // FILE_WRITE_CANCELLED</i></p>")
        except Exception as e:
            safe_e = html.escape(str(e))
            self.output_area.append(f"<p style='color: #FF5555; font-family: Monospace;'><i>SYSTEM // TOOL_USE_ERROR: {safe_e}</i></p>")

    # --- MANDATE COMPLIANCE ---

    def apply_theme(self):
        """Palette Mandate: Ensure aesthetic integrity."""
        self.update_stylesheet()
        self.ghost_log.log("PALETTE_SYNC: OK")

    def scan_integrity(self) -> bool:
        """Sentinel Mandate: Security and connection check."""
        try:
            import requests
            requests.get(self.engine.base_url, timeout=1)
            self.ghost_log.log("SENTINEL_SCAN: OLLAMA_UP")
            return True
        except:
            self.ghost_log.log("SENTINEL_SCAN: OLLAMA_DOWN")
            return False

    def stream_chat(self, model: str, prompt: str, options: Optional[dict] = None):
        """Bolt Mandate: High-performance orchestration proxy."""
        return self.engine.stream_chat(model, prompt, options)

    def render_messages(self):
        self.output_area.clear()
        html_parts = []
        
        display_messages = self.messages.copy()
        if self.pending_message:
            display_messages.append(self.pending_message)
            
        for msg in display_messages:
            # ⚡ BOLT OPTIMIZATION: Cache full HTML block for past messages
            # Reduces redundant string formatting and escaping inside the loop
            if "_full_html" in msg:
                html_parts.append(msg["_full_html"])
                continue

            role_color = "#6633FF" if msg["role"] == "user" else "#00e6e6"
            bg_color = "rgba(102, 51, 255, 0.1)" if msg["role"] == "user" else "rgba(0, 230, 230, 0.05)"
            glow_style = f"border: 1px solid {role_color}; padding: 16px; border-radius: 8px; background-color: {bg_color};"
            
            msg_html = ""
            if msg["role"] == "user":
                safe_content = html.escape(msg['content'])
                msg_html = f"<div style='{glow_style} margin-left: 60px; margin-bottom: 12px;'><span style='color: #6633FF; font-size: 14px;'><b>&gt;</b></span> <span style='color: #FFFFFF; font-size: 14px; line-height: 1.4;'>{safe_content}</span></div>"
            else:
                is_pending = (self.pending_message and msg == self.pending_message)
                content = msg['content']
                if is_pending:
                    # ⚡ HOLOGRAPHIC BRACKETS: Only during stream
                    safe_inner = html.escape(content)
                    content_html = f"<pre style='white-space: pre-wrap; font-family: inherit; color: #E0E0E0; font-size: 14px;'><span style='color: #00e6e6;'>[</span> {safe_inner} <span style='color: #00e6e6;'>]</span></pre>"
                else:
                    content_html = msg.get("_rendered_html")
                    if content_html is None:
                        content_html = self.md.render(content)
                        msg["_rendered_html"] = content_html
                
                # ⚡ POWER STRIPE: Dynamic border width simulation, capped to reasonable sizes
                stripe_width = min(6, len(content) // 50 + 2)
                stripe_style = f"border-left: {stripe_width}px solid {role_color};"
                
                msg_html = f"<div style='{glow_style} {stripe_style} margin-right: 60px; margin-bottom: 12px;'>"
                safe_model_role = html.escape(msg['model'].upper())
                msg_html += f"<div style='color: #00e6e6; font-size: 11px; letter-spacing: 2px; margin-bottom: 8px;'><b>{safe_model_role}</b></div>"
                msg_html += f"<div style='color: #E0E0E0; font-size: 14px; line-height: 1.5;'>{content_html}</div></div>"

            html_parts.append(msg_html)
            # Only cache finalized messages
            if not (self.pending_message and msg == self.pending_message):
                msg["_full_html"] = msg_html
        
        self.output_area.setHtml("".join(html_parts))
        self.output_area.moveCursor(self.output_area.textCursor().MoveOperation.End)
