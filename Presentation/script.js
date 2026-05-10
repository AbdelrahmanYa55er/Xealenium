const slides = Array.from(document.querySelectorAll(".slide"));
const progressFill = document.getElementById("progressFill");
const slideCounter = document.getElementById("slideCounter");
const previousButton = document.getElementById("prevSlide");
const nextButton = document.getElementById("nextSlide");

let activeIndex = 0;

function formatCount(value) {
  return String(value).padStart(2, "0");
}

function readHashIndex() {
  const value = Number.parseInt(window.location.hash.replace("#", ""), 10);
  if (Number.isNaN(value)) {
    return 0;
  }
  return Math.min(Math.max(value - 1, 0), slides.length - 1);
}

function showSlide(index, pushHash = true) {
  activeIndex = Math.min(Math.max(index, 0), slides.length - 1);

  slides.forEach((slide, slideIndex) => {
    slide.classList.toggle("is-active", slideIndex === activeIndex);
  });

  const current = activeIndex + 1;
  const progress = (current / slides.length) * 100;
  progressFill.style.width = `${progress}%`;
  slideCounter.textContent = `${formatCount(current)} / ${formatCount(slides.length)}`;

  previousButton.disabled = activeIndex === 0;
  nextButton.disabled = activeIndex === slides.length - 1;

  document.title = `${slides[activeIndex].dataset.title} | Xealenium`;

  if (pushHash) {
    const hash = `#${current}`;
    if (window.location.hash !== hash) {
      history.replaceState(null, "", hash);
    }
  }
}

function nextSlide() {
  showSlide(activeIndex + 1);
}

function previousSlide() {
  showSlide(activeIndex - 1);
}

previousButton.addEventListener("click", previousSlide);
nextButton.addEventListener("click", nextSlide);

window.addEventListener("keydown", (event) => {
  const forwardKeys = ["ArrowRight", "ArrowDown", "PageDown", " "];
  const backwardKeys = ["ArrowLeft", "ArrowUp", "PageUp", "Backspace"];

  if (forwardKeys.includes(event.key)) {
    event.preventDefault();
    nextSlide();
  }

  if (backwardKeys.includes(event.key)) {
    event.preventDefault();
    previousSlide();
  }
});

window.addEventListener("hashchange", () => {
  showSlide(readHashIndex(), false);
});

let touchStartX = null;

window.addEventListener("touchstart", (event) => {
  touchStartX = event.changedTouches[0].clientX;
}, { passive: true });

window.addEventListener("touchend", (event) => {
  if (touchStartX === null) {
    return;
  }

  const delta = event.changedTouches[0].clientX - touchStartX;
  if (Math.abs(delta) > 60) {
    if (delta < 0) {
      nextSlide();
    } else {
      previousSlide();
    }
  }
  touchStartX = null;
}, { passive: true });

showSlide(readHashIndex());
