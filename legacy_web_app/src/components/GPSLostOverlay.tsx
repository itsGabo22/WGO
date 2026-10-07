"use client";

import React from "react";
import { motion, AnimatePresence } from "framer-motion";
import { MapPinOff } from "lucide-react";

interface GPSLostOverlayProps {
  isVisible: boolean;
}

export function GPSLostOverlay({ isVisible }: GPSLostOverlayProps) {
  return (
    <AnimatePresence>
      {isVisible && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.3 }}
          className="absolute inset-0 z-40 flex flex-col items-center justify-center bg-black/85 backdrop-blur-sm pointer-events-auto"
        >
          <motion.div
            initial={{ scale: 0.8 }}
            animate={{ scale: 1 }}
            exit={{ scale: 0.8 }}
            transition={{ type: "spring", stiffness: 200, damping: 20 }}
            className="flex flex-col items-center gap-3 p-6 rounded-3xl bg-black border border-[#BF3003]"
          >
            <motion.div
              animate={{ opacity: [1, 0.5, 1] }}
              transition={{ duration: 1.5, repeat: Infinity, ease: "easeInOut" }}
              className="text-[#BF3003]"
            >
              <MapPinOff size={40} strokeWidth={2} />
            </motion.div>
            <h2 className="text-white text-[16px] font-bold tracking-tight text-center">
              Buscando señal GPS...
            </h2>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
