"use client";

import { useState, useEffect } from "react";
import { ScreenListening } from "@/components/ScreenListening";
import { ScreenModeSelection } from "@/components/ScreenModeSelection";

import { motion, AnimatePresence } from "framer-motion";

export default function Home() {
  const [currentScreen, setCurrentScreen] = useState<"listening" | "mode">("listening");
  const [isListening, setIsListening] = useState(false);

  useEffect(() => {
    let timeout: NodeJS.Timeout;
    if (isListening) {
      timeout = setTimeout(() => {
        setIsListening(false);
        setCurrentScreen("mode");
      }, 3000);
    }
    return () => clearTimeout(timeout);
  }, [isListening]);

  const handleToggleListen = () => setIsListening(true);
  const handleCancel = () => {
    setIsListening(false);
    setCurrentScreen("listening");
  };
  const handleSelectMode = (mode: "drive" | "walk") => {
    console.log(`Selected mode: ${mode}`);
    setCurrentScreen("listening");
  };

  return (
    <main className="flex h-full w-full bg-background relative overflow-hidden">
      <AnimatePresence mode="wait">
        {currentScreen === "listening" ? (
          <motion.div 
            key="listening"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -20 }}
            transition={{ duration: 0.3 }}
            className="w-full h-full absolute inset-0"
          >
            <ScreenListening isListening={isListening} onToggleListen={handleToggleListen} onCancel={handleCancel} />
          </motion.div>
        ) : (
          <motion.div 
            key="mode"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            transition={{ duration: 0.3 }}
            className="w-full h-full absolute inset-0"
          >
            <ScreenModeSelection onSelectMode={handleSelectMode} />
          </motion.div>
        )}
      </AnimatePresence>
    </main>
  );
}
